package com.superjoin.fact_knowledge_layer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractionResponseDTO {
    public List<ExtractedFactDTO> facts = new ArrayList<>();
}
