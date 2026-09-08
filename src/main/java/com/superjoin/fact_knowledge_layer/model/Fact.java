package com.superjoin.fact_knowledge_layer.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single fact extracted from a document, grounded in a verbatim evidence
 * quote. The schema is intentionally minimal (subject/metric/value/unit) plus
 * an open "attributes" bag (see JsonMapConverter) so the model generalizes to
 * fact kinds we did not anticipate (dates, roles, addresses, ratios, etc.)
 * without code or schema changes.
 */
@Entity
@Table(name = "fact")
public class Fact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private SourceDocument document;

    /** What the fact is about, e.g. "Total Revenue", "Board Director - John Smith". */
    @Column(nullable = false, length = 500)
    private String subject;

    /** The metric/predicate, e.g. "annual revenue", "employment status". Free text,
     *  normalized (lowercased, trimmed) at extraction time for easier matching. */
    @Column(name = "metric_key", nullable = false, length = 300)
    private String metricKey;

    /** The stated value, as text (e.g. "12.4 million", "resigned", "Mumbai, India"). */
    @Column(name = "value_text", nullable = false, length = 1000)
    private String value;

    /** Parsed numeric value when factType == NUMERIC and parsing succeeded, else null. */
    @Column(name = "numeric_value")
    private Double numericValue;

    @Column(length = 100)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "fact_type", nullable = false)
    private FactType factType;

    /** Verbatim substring copied from the document's extracted text - the grounding evidence. */
    @Lob
    @Column(name = "evidence_quote", nullable = false)
    private String evidenceQuote;

    /** 0..1 self-reported confidence from the extraction model. */
    @Column(nullable = false)
    private double confidence;

    /** True when confidence is below the configured threshold or the model
     *  flagged the extraction as unreliable - shown in the UI as a known failure
     *  rather than silently trusted. */
    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    /** Open attribute bag: period, scope, region, conditions, role, etc.
     *  Whatever the source document actually supports. */
    @Convert(converter = JsonMapConverter.class)
    @Lob
    @Column(name = "attributes")
    private Map<String, Object> attributes = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // --- getters/setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SourceDocument getDocument() { return document; }
    public void setDocument(SourceDocument document) { this.document = document; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getMetricKey() { return metricKey; }
    public void setMetricKey(String metricKey) { this.metricKey = metricKey; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public Double getNumericValue() { return numericValue; }
    public void setNumericValue(Double numericValue) { this.numericValue = numericValue; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public FactType getFactType() { return factType; }
    public void setFactType(FactType factType) { this.factType = factType; }

    public String getEvidenceQuote() { return evidenceQuote; }
    public void setEvidenceQuote(String evidenceQuote) { this.evidenceQuote = evidenceQuote; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public boolean isNeedsReview() { return needsReview; }
    public void setNeedsReview(boolean needsReview) { this.needsReview = needsReview; }

    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }

    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
