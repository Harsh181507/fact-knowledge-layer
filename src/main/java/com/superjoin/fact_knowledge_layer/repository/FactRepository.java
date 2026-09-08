package com.superjoin.fact_knowledge_layer.repository;

import com.superjoin.fact_knowledge_layer.model.Fact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FactRepository extends JpaRepository<Fact, Long> {

    List<Fact> findByDocument_Id(Long documentId);

    /** All facts from a different document, used as the raw candidate pool for
     *  fuzzy cross-document matching in FactComparisonService (exact metricKey
     *  matching alone misses same-metric facts the model labeled differently
     *  across chunks/documents). */
    List<Fact> findByDocument_IdNot(Long documentId);

    /** Coarse candidate pool for cross-document comparison: same normalized
     *  metric key, from a different document than the given one. Kept as a
     *  fast path alongside the fuzzy pool above. */
    List<Fact> findByMetricKeyAndDocument_IdNot(String metricKey, Long documentId);
}
