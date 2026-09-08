package com.superjoin.fact_knowledge_layer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors the JSON object shape we instruct the LLM to emit for each fact.
 * Kept intentionally loose (attributes is a free-form map) so new kinds of
 * facts don't require code changes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractedFactDTO {

    public String subject;
    public String metric;
    public String value;
    public String unit;
    public String factType;      // "NUMERIC" or "SEMANTIC"
    public double confidence;    // 0..1
    public String evidenceQuote; // verbatim substring of the source text
    public Map<String, Object> attributes = new LinkedHashMap<>();

    public boolean isPlausible() {
        return subject != null && !subject.isBlank()
                && metric != null && !metric.isBlank()
                && value != null && !value.isBlank()
                && evidenceQuote != null && !evidenceQuote.isBlank();
    }
}
