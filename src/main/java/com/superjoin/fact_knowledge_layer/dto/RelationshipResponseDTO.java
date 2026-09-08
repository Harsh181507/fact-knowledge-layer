package com.superjoin.fact_knowledge_layer.dto;

import com.superjoin.fact_knowledge_layer.model.FactRelationship;

public class RelationshipResponseDTO {
    public Long id;
    public String relationType;
    public String explanation;
    public FactResponseDTO factA;
    public FactResponseDTO factB;

    public static RelationshipResponseDTO from(FactRelationship rel) {
        RelationshipResponseDTO dto = new RelationshipResponseDTO();
        dto.id = rel.getId();
        dto.relationType = rel.getRelationType().name();
        dto.explanation = rel.getExplanation();
        dto.factA = FactResponseDTO.from(rel.getFactA());
        dto.factB = FactResponseDTO.from(rel.getFactB());
        return dto;
    }
}
