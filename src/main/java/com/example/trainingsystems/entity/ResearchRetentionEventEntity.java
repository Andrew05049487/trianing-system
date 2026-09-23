package com.example.trainingsystems.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "research_retention_events")
@Data
public class ResearchRetentionEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "sample_id", nullable = false, length = 36)
    private String sampleId;
    @Column(name = "policy_version", length = 64)
    private String policyVersion;
    @Column(name = "reason", nullable = false, length = 24)
    private String reason;
    @Column(name = "actor_user_id")
    private Long actorUserId;
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
