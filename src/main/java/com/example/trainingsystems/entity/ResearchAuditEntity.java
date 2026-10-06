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
@Table(name = "research_audit")
@Data
public class ResearchAuditEntity {
    @Column(name = "schema_version")
    private Integer schemaVersion;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "sample_id", length = 36)
    private String sampleId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
