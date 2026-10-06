package com.example.trainingsystems.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

/** Immutable snapshot written on every annotation/review transition. */
@Entity
@Table(name = "research_annotation_revisions")
@Data
public class ResearchAnnotationRevisionEntity {
    @Column(name = "schema_version")
    private Integer schemaVersion;
    @Column(name="reason_code", length=64)
    private String reasonCode;
    @Column(name="disposition", length=32)
    private String disposition;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sample_id", nullable = false, length = 36)
    private String sampleId;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "annotator_user_id", nullable = false)
    private Long annotatorUserId;

    @Column(name = "label", nullable = false, length = 32)
    private String label;

    @Column(name = "note", columnDefinition = "VARCHAR(1000)")
    private String note;

    @Column(name = "label_version", nullable = false, length = 64)
    private String labelVersion;

    @Column(name = "action_definition_version", nullable = false, length = 64)
    private String actionDefinitionVersion;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "reviewer_user_id")
    private Long reviewerUserId;

    @Column(name = "review_note", columnDefinition = "VARCHAR(1000)")
    private String reviewNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
