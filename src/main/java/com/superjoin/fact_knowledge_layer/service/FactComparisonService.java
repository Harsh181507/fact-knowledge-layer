package com.superjoin.fact_knowledge_layer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superjoin.fact_knowledge_layer.dto.ComparisonResultDTO;
import com.superjoin.fact_knowledge_layer.exception.LlmException;
import com.superjoin.fact_knowledge_layer.model.Fact;
import com.superjoin.fact_knowledge_layer.model.FactRelationship;
import com.superjoin.fact_knowledge_layer.model.RelationType;
import com.superjoin.fact_knowledge_layer.repository.FactRelationshipRepository;
import com.superjoin.fact_knowledge_layer.repository.FactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cross-document reasoning: for every new fact, find other facts that talk
 * about the same metric (coarse blocking via the normalized metricKey - see
 * FactRepository.findByMetricKeyAndDocument_IdNot) and ask the LLM whether the
 * pair corroborates, contradicts, or is reconciled by context. This blocking
 * step is what keeps comparison cost roughly linear in the number of facts
 * instead of quadratic across the whole corpus, so it stays workable as more
 * PDFs are added.
 */
@Service
public class FactComparisonService {

    private static final Logger log = LoggerFactory.getLogger(FactComparisonService.class);

    private static final String SYSTEM_PROMPT = """
            You compare two facts extracted from two (possibly different) documents to decide how they
            relate. Both facts are about the same general metric, but may use different wording, units,
            or time periods.

            Classify the relationship as exactly one of:
            - "CORROBORATES": both facts state the same real-world thing, even if worded, rounded, or
              formatted differently (e.g. same revenue figure in different currency notation, same
              status described in different words).
            - "CONTRADICTS": the facts cannot both be true as stated - there is no reasonable reading of
              context (time, scope, units) that reconciles them.
            - "RECONCILED_BY_CONTEXT": the facts look different or even contradictory at first glance,
              but are both true once you account for context such as different time periods, different
              scopes/regions, different units, or a status change over time (e.g. a director resigned
              between the two documents' dates).
            - "UNRELATED": on closer inspection these are not actually comparable (different subjects
              entirely) - use sparingly, since blocking already filtered by metric.

            Respond with ONLY a JSON object of this exact shape, no prose, no markdown fences:
            {"relation": "CORROBORATES" | "CONTRADICTS" | "RECONCILED_BY_CONTEXT" | "UNRELATED",
             "explanation": "one or two sentences citing the specific values/context that justify this"}
            """;

    private final GeminiClientService llm;
    private final FactRepository factRepository;
    private final FactRelationshipRepository relationshipRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public FactComparisonService(GeminiClientService llm,
                                  FactRepository factRepository,
                                  FactRelationshipRepository relationshipRepository) {
        this.llm = llm;
        this.factRepository = factRepository;
        this.relationshipRepository = relationshipRepository;
    }

    public List<FactRelationship> compareAgainstExisting(List<Fact> newFacts) {
        List<FactRelationship> created = new java.util.ArrayList<>();
        for (Fact fact : newFacts) {
            for (Fact candidate : findCandidates(fact)) {
                if (alreadyCompared(fact, candidate)) {
                    continue;
                }
                try {
                    ComparisonResultDTO result = compare(fact, candidate);
                    if (result == null || "UNRELATED".equalsIgnoreCase(result.relation)) {
                        continue; // not worth persisting a non-relationship
                    }
                    FactRelationship rel = new FactRelationship();
                    rel.setFactA(fact);
                    rel.setFactB(candidate);
                    rel.setRelationType(parseRelation(result.relation));
                    rel.setExplanation(result.explanation != null ? result.explanation : "");
                    created.add(relationshipRepository.save(rel));
                } catch (LlmException e) {
                    log.warn("Comparison failed between fact {} and {}: {}", fact.getId(), candidate.getId(), e.getMessage());
                }
            }
        }
        return created;
    }

    /**
     * Finds other-document facts worth comparing against. An exact metricKey
     * match is a fast path, but the LLM often labels the same real-world
     * metric slightly differently between chunks/documents ("revenue" vs
     * "annual revenue" vs "total revenue"), so exact match alone misses most
     * genuine overlaps. We fall back to token-overlap similarity on the
     * metric label (and, more loosely, on subject+metric together) so
     * differently-worded mentions of the same thing still get compared,
     * without hard-coding any subject-matter-specific rules.
     */
    private List<Fact> findCandidates(Fact fact) {
        Map<Long, Fact> candidates = new java.util.LinkedHashMap<>();
        for (Fact f : factRepository.findByMetricKeyAndDocument_IdNot(fact.getMetricKey(), fact.getDocument().getId())) {
            candidates.put(f.getId(), f);
        }

        Set<String> factMetricTokens = tokenize(fact.getMetricKey());
        Set<String> factSubjectTokens = tokenize(fact.getSubject());

        for (Fact other : factRepository.findByDocument_IdNot(fact.getDocument().getId())) {
            if (candidates.containsKey(other.getId())) {
                continue;
            }
            Set<String> otherMetricTokens = tokenize(other.getMetricKey());
            double metricSimilarity = jaccard(factMetricTokens, otherMetricTokens);

            if (metricSimilarity >= 0.5) {
                candidates.put(other.getId(), other);
                continue;
            }
            // Looser bar when the subject also overlaps (likely the same
            // real-world entity being described), since that context makes a
            // partial metric-label match more meaningful.
            double subjectSimilarity = jaccard(factSubjectTokens, tokenize(other.getSubject()));
            if (metricSimilarity >= 0.25 && subjectSimilarity > 0) {
                candidates.put(other.getId(), other);
            }
        }
        return new java.util.ArrayList<>(candidates.values());
    }

    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "of", "for", "and", "or", "to", "in", "on", "at", "is", "are", "was", "were",
            "per", "as", "by", "with", "from");

    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new java.util.HashSet<>();
        for (String word : text.toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9]+")) {
            if (word.length() > 2 && !STOPWORDS.contains(word)) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new java.util.HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new java.util.HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    private boolean alreadyCompared(Fact a, Fact b) {
        return relationshipRepository.existsByFactA_IdAndFactB_Id(a.getId(), b.getId())
                || relationshipRepository.existsByFactA_IdAndFactB_Id(b.getId(), a.getId());
    }

    private ComparisonResultDTO compare(Fact a, Fact b) {
        String prompt = """
                Fact A (from document "%s"):
                  subject: %s
                  metric: %s
                  value: %s %s
                  evidence quote: "%s"
                  attributes: %s

                Fact B (from document "%s"):
                  subject: %s
                  metric: %s
                  value: %s %s
                  evidence quote: "%s"
                  attributes: %s
                """.formatted(
                a.getDocument().getFilename(), a.getSubject(), a.getMetricKey(), a.getValue(), nullToEmpty(a.getUnit()), a.getEvidenceQuote(), a.getAttributes(),
                b.getDocument().getFilename(), b.getSubject(), b.getMetricKey(), b.getValue(), nullToEmpty(b.getUnit()), b.getEvidenceQuote(), b.getAttributes()
        );

        String raw = llm.complete(SYSTEM_PROMPT, prompt);
        String json = GeminiClientService.stripCodeFences(raw);
        try {
            return mapper.readValue(json, ComparisonResultDTO.class);
        } catch (Exception e) {
            throw new LlmException("Could not parse comparison JSON: " + e.getMessage(), e);
        }
    }

    private static RelationType parseRelation(String s) {
        try {
            return RelationType.valueOf(s.toUpperCase());
        } catch (Exception e) {
            return RelationType.UNCERTAIN;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
