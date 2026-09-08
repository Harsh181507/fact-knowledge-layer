package com.superjoin.fact_knowledge_layer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ComparisonResultDTO {
    /** One of: CORROBORATES, CONTRADICTS, RECONCILED_BY_CONTEXT, UNRELATED, UNCERTAIN */
    public String relation;
    public String explanation;
}
