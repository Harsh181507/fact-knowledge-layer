package com.superjoin.fact_knowledge_layer.model;

/**
 * The kind of relationship discovered between two facts that were extracted
 * from (possibly different) documents.
 */
public enum RelationType {
    /** The two facts state the same thing, possibly in different words/units. */
    CORROBORATES,
    /** The two facts genuinely conflict and cannot both be true as stated. */
    CONTRADICTS,
    /** The two facts look contradictory but are both true once context
     *  (time period, scope, units, conditions) is taken into account. */
    RECONCILED_BY_CONTEXT,
    /** The comparison could not be made confidently - kept for transparency
     *  about extraction/reasoning limitations rather than hidden. */
    UNCERTAIN
}
