package com.superjoin.fact_knowledge_layer.repository;

import com.superjoin.fact_knowledge_layer.model.FactRelationship;
import com.superjoin.fact_knowledge_layer.model.RelationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FactRelationshipRepository extends JpaRepository<FactRelationship, Long> {

    List<FactRelationship> findByRelationType(RelationType relationType);

    boolean existsByFactA_IdAndFactB_Id(Long factAId, Long factBId);
}
