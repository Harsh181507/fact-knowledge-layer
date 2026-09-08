package com.superjoin.fact_knowledge_layer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superjoin.fact_knowledge_layer.config.LlmProperties;
import com.superjoin.fact_knowledge_layer.exception.LlmException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Talks to Google's free-tier Gemini API (generateContent) and returns the
 * model's raw text reply. Get a free key (no credit card) at
 * https://aistudio.google.com/apikey and export it as GEMINI_API_KEY.
 *
 * Kept generic (prompt in, text out) - the extraction and comparison
 * services own their own prompts and response schemas.
 */
@Service
public class GeminiClientService {

    private final RestClient restClient;
    private final LlmProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeminiClientService(RestClient restClient, LlmProperties props) {
        this.restClient = restClient;
        this.props = props;
    }

    public String complete(String systemPrompt, String userPrompt) {
        if (props.getKey() == null || props.getKey().isBlank()) {
            throw new LlmException(
                    "GEMINI_API_KEY is not set. Get a free key at https://aistudio.google.com/apikey " +
                            "and export it as an environment variable before starting the app.");
        }

        // Gemini's free tier occasionally returns 503 "high demand" errors that
        // clear up within a few seconds - retry a handful of times with
        // exponential backoff before giving up on this chunk/comparison.
        final int maxAttempts = 4;
        LlmException lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return callOnce(systemPrompt, userPrompt);
            } catch (LlmException e) {
                lastError = e;
                boolean retryable = e.getMessage() != null &&
                        (e.getMessage().contains("503") || e.getMessage().contains("UNAVAILABLE")
                                || e.getMessage().contains("429"));
                if (!retryable || attempt == maxAttempts) {
                    throw e;
                }
                long backoffMs = (long) Math.pow(2, attempt) * 1000; // 2s, 4s, 8s
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw lastError;
    }

    private String callOnce(String systemPrompt, String userPrompt) {
        String uri = props.getUrl() + "/" + props.getModel() + ":generateContent?key=" + props.getKey();

        // responseMimeType=application/json forces Gemini to return pure JSON,
        // no markdown fences or preamble to strip.
        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "maxOutputTokens", props.getMaxTokens()
                )
        );

        try {
            String rawResponse = restClient.post()
                    .uri(uri)
                    .header("content-type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = mapper.readTree(rawResponse);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                String blockReason = root.path("promptFeedback").path("blockReason").asText(null);
                throw new LlmException(blockReason != null
                        ? "Gemini blocked this request: " + blockReason
                        : "Gemini API returned no candidates: " + rawResponse);
            }

            JsonNode parts = candidates.get(0).path("content").path("parts");
            StringBuilder text = new StringBuilder();
            for (JsonNode part : parts) {
                if (part.has("text")) {
                    text.append(part.path("text").asText());
                }
            }
            if (text.isEmpty()) {
                throw new LlmException("Gemini API returned no text content: " + rawResponse);
            }
            return text.toString();
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Gemini API call failed: " + e.getMessage(), e);
        }
    }

    /** Strips markdown code fences (```json ... ```) as a safety net, even though
     *  responseMimeType=application/json should make this unnecessary. */
    public static String stripCodeFences(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline != -1) {
                t = t.substring(firstNewline + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.trim();
    }
}
