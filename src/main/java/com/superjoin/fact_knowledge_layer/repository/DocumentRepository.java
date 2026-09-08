package com.superjoin.fact_knowledge_layer.repository;

import com.superjoin.fact_knowledge_layer.model.SourceDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<SourceDocument, Long> {
}
