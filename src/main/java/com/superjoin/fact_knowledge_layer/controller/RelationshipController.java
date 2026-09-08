package com.superjoin.fact_knowledge_layer.controller;

import com.superjoin.fact_knowledge_layer.dto.RelationshipResponseDTO;
import com.superjoin.fact_knowledge_layer.model.FactRelationship;
import com.superjoin.fact_knowledge_layer.model.RelationType;
import com.superjoin.fact_knowledge_layer.repository.FactRelationshipRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/relationships")
public class RelationshipController {

    private final FactRelationshipRepository relationshipRepository;

    public RelationshipController(FactRelationshipRepository relationshipRepository) {
        this.relationshipRepository = relationshipRepository;
    }

    /** e.g. GET /api/relationships?type=CONTRADICTS */
    @GetMapping
    public List<RelationshipResponseDTO> list(@RequestParam(required = false) String type) {
        List<FactRelationship> rels = (type == null)
                ? relationshipRepository.findAll()
                : relationshipRepository.findByRelationType(RelationType.valueOf(type.toUpperCase()));
        return rels.stream().map(RelationshipResponseDTO::from).toList();
    }

    @GetMapping("/{id}")
    public RelationshipResponseDTO get(@PathVariable Long id) {
        FactRelationship rel = relationshipRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Relationship " + id + " not found"));
        return RelationshipResponseDTO.from(rel);
    }
}
