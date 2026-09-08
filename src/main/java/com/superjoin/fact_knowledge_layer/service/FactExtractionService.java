package com.superjoin.fact_knowledge_layer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superjoin.fact_knowledge_layer.dto.ExtractedFactDTO;
import com.superjoin.fact_knowledge_layer.dto.ExtractionResponseDTO;
import com.superjoin.fact_knowledge_layer.exception.LlmException;
import com.superjoin.fact_knowledge_layer.model.Fact;
import com.superjoin.fact_knowledge_layer.model.FactType;
import com.superjoin.fact_knowledge_layer.model.SourceDocument;
import com.superjoin.fact_knowledge_layer.repository.FactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class FactExtractionService {

    private static final Logger log = LoggerFactory.getLogger(FactExtractionService.class);

    private static final String SYSTEM_PROMPT = """
            You extract meaningful facts from a business/technical/legal document for a fact-checking
            knowledge base. A fact is any concrete, checkable statement: a number (revenue, headcount,
            percentage, date, duration), a status (active/resigned, open/closed), a relationship
            (address, ownership, role), or any other claim that could later be compared against a claim
            from a different document about the same real-world subject.

            Rules:
            - Only extract facts that are explicitly stated in the text. Do not infer or calculate values
              that are not written down.
            - "evidenceQuote" MUST be an exact, verbatim substring copied from the provided text
              (same words, same order, same punctuation) - copy/paste, do not paraphrase. This is what
              a human will use to verify the fact against the source, so it must be findable in the text.
            - "metric" should be a short, normalized, lowercase label for what is being measured or
              stated (e.g. "annual revenue", "employee count", "director employment status",
              "registered office address"), consistent enough that the same real-world metric in a
              different document would get the same label.
            - Put any qualifying context that could matter for comparison (time period, fiscal year,
              scope/region, currency, conditions, as-of date) into "attributes" as extra keys - whatever
              the document actually supports, do not invent keys that aren't backed by the text.
            - "factType" is "NUMERIC" if the value is fundamentally a number/quantity/date, otherwise
              "SEMANTIC".
            - "confidence" (0.0-1.0) should reflect how explicit and unambiguous the statement is in the
              text. Use low confidence (below 0.4) for facts you are including but are genuinely unsure
              about (e.g. ambiguous phrasing, unclear referent, partially cut-off text) - do not just omit
              those, include them at low confidence so the failure is visible rather than silent.
            - Skip boilerplate, headers/footers, and page numbers.
            - Return between 0 and 40 facts for the given text, prioritizing the most significant and
              comparison-relevant ones if there are more candidates than that.

            Respond with ONLY a JSON object of this exact shape, no prose, no markdown fences:
            {"facts": [
              {
                "subject": "string",
                "metric": "string",
                "value": "string",
                "unit": "string or empty string",
                "factType": "NUMERIC" | "SEMANTIC",
                "confidence": 0.0,
                "evidenceQuote": "string",
                "attributes": { "anyKey": "anyValue" }
              }
            ]}
            """;

    private final GeminiClientService llm;
    private final FactRepository factRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${fkl.extraction.chunk-size:12000}")
    private int chunkSize;

    @Value("${fkl.extraction.chunk-overlap:500}")
    private int chunkOverlap;

    @Value("${fkl.extraction.min-confidence:0.4}")
    private double minConfidence;

    public FactExtractionService(GeminiClientService llm, FactRepository factRepository) {
        this.llm = llm;
        this.factRepository = factRepository;
    }

    public List<Fact> extractAndPersist(SourceDocument document) {
        List<String> chunks = chunk(document.getRawText());
        log.info("Extracting facts from '{}' in {} chunk(s)", document.getFilename(), chunks.size());

        List<Fact> saved = new ArrayList<>();
        // Cheap dedup across chunk overlaps: same metric+value+evidence pair shouldn't be saved twice.
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            List<ExtractedFactDTO> extracted;
            try {
                extracted = callLlmForChunk(chunk);
            } catch (LlmException e) {
                log.warn("Extraction failed for chunk {} of '{}': {}", i, document.getFilename(), e.getMessage());
                continue; // one bad chunk shouldn't fail the whole document
            }

            for (ExtractedFactDTO dto : extracted) {
                if (!dto.isPlausible()) {
                    continue;
                }
                // Ground the evidence: only keep facts whose quote is actually
                // findable in the source text (guards against hallucinated evidence).
                if (!document.getRawText().contains(dto.evidenceQuote.trim())) {
                    log.debug("Dropping fact with unverifiable evidence quote: {}", dto.evidenceQuote);
                    continue;
                }

                String dedupeKey = normalize(dto.metric) + "|" + normalize(dto.value) + "|" + normalize(dto.evidenceQuote);
                if (!seen.add(dedupeKey)) {
                    continue;
                }

                Fact fact = toEntity(dto, document);
                saved.add(factRepository.save(fact));
            }
        }
        return saved;
    }

    private List<ExtractedFactDTO> callLlmForChunk(String chunkText) {
        String userPrompt = "Document text:\n---\n" + chunkText + "\n---";
        String raw = llm.complete(SYSTEM_PROMPT, userPrompt);
        String json = GeminiClientService.stripCodeFences(raw);
        try {
            ExtractionResponseDTO response = mapper.readValue(json, ExtractionResponseDTO.class);
            return response.facts != null ? response.facts : List.of();
        } catch (Exception e) {
            throw new LlmException("Could not parse extraction JSON: " + e.getMessage(), e);
        }
    }

    private Fact toEntity(ExtractedFactDTO dto, SourceDocument document) {
        Fact fact = new Fact();
        fact.setDocument(document);
        fact.setSubject(dto.subject.trim());
        fact.setMetricKey(normalize(dto.metric));
        fact.setValue(dto.value.trim());
        fact.setUnit(dto.unit == null ? "" : dto.unit.trim());
        fact.setEvidenceQuote(dto.evidenceQuote.trim());
        fact.setConfidence(clamp01(dto.confidence));
        fact.setAttributes(dto.attributes == null ? new java.util.LinkedHashMap<>() : dto.attributes);

        FactType type = "NUMERIC".equalsIgnoreCase(dto.factType) ? FactType.NUMERIC : FactType.SEMANTIC;
        fact.setFactType(type);
        if (type == FactType.NUMERIC) {
            fact.setNumericValue(parseLeadingNumber(dto.value));
        }

        boolean lowConfidence = fact.getConfidence() < minConfidence;
        fact.setNeedsReview(lowConfidence);
        if (lowConfidence) {
            fact.setReviewNote("Below confidence threshold (" + fact.getConfidence() + "); statement may be ambiguous or incomplete in the source.");
        }
        return fact;
    }

    private List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text.length() <= chunkSize) {
            chunks.add(text);
            return chunks;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
            start = end - chunkOverlap;
        }
        return chunks;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static Double parseLeadingNumber(String value) {
        if (value == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("-?[0-9]+(\\.[0-9]+)?")
                .matcher(value.replace(",", ""));
        if (m.find()) {
            try {
                return Double.parseDouble(m.group());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
