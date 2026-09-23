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
@Table(name = "research_export_audit")
@Data
public class ResearchExportAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;
    @Column(name = "study_id", nullable = false, length = 64)
    private String studyId;
    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;
    @Column(name = "sample_count", nullable = false)
    private int sampleCount;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
