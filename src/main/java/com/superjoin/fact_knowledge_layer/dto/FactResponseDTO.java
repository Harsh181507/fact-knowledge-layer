package com.superjoin.fact_knowledge_layer.dto;

import com.superjoin.fact_knowledge_layer.model.Fact;

import java.util.Map;

public class FactResponseDTO {
    public Long id;
    public Long documentId;
    public String documentFilename;
    public String subject;
    public String metricKey;
    public String value;
    public Double numericValue;
    public String unit;
    public String factType;
    public String evidenceQuote;
    public double confidence;
    public boolean needsReview;
    public String reviewNote;
    public Map<String, Object> attributes;

    public static FactResponseDTO from(Fact f) {
        FactResponseDTO dto = new FactResponseDTO();
        dto.id = f.getId();
        dto.documentId = f.getDocument().getId();
        dto.documentFilename = f.getDocument().getFilename();
        dto.subject = f.getSubject();
        dto.metricKey = f.getMetricKey();
        dto.value = f.getValue();
        dto.numericValue = f.getNumericValue();
        dto.unit = f.getUnit();
        dto.factType = f.getFactType() != null ? f.getFactType().name() : null;
        dto.evidenceQuote = f.getEvidenceQuote();
        dto.confidence = f.getConfidence();
        dto.needsReview = f.isNeedsReview();
        dto.reviewNote = f.getReviewNote();
        dto.attributes = f.getAttributes();
        return dto;
    }
}
