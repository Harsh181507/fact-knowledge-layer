package com.superjoin.fact_knowledge_layer.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "fact_relationship")
public class FactRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fact_a_id", nullable = false)
    private Fact factA;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fact_b_id", nullable = false)
    private Fact factB;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RelationType relationType;

    /** The model's plain-language reasoning for why this relation holds -
     *  what makes them corroborate, contradict, or reconcile. */
    @Lob
    @Column(name = "explanation", nullable = false)
    private String explanation;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Fact getFactA() { return factA; }
    public void setFactA(Fact factA) { this.factA = factA; }

    public Fact getFactB() { return factB; }
    public void setFactB(Fact factB) { this.factB = factB; }

    public RelationType getRelationType() { return relationType; }
    public void setRelationType(RelationType relationType) { this.relationType = relationType; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
