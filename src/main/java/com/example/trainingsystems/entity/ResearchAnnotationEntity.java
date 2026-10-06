package com.example.trainingsystems.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "research_annotations")
@Data
public class ResearchAnnotationEntity {
    @Column(name = "schema_version")
    private Integer schemaVersion;

    @Id
    @Column(name = "sample_id", length = 36)
    private String sampleId;

    @Column(name = "therapist_user_id", nullable = false)
    private Long therapistUserId;

    @Column(name = "label", nullable = false, length = 32)
    private String label;

    @Column(name = "note", columnDefinition = "VARCHAR(1000)")
    private String note;

    @Column(name = "label_version", nullable = false, length = 64)
    private String labelVersion;

    @Column(name = "action_definition_version", nullable = false, length = 64)
    private String actionDefinitionVersion;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "LABELED";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "revision", nullable = false)
    private int revision = 0;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewer_user_id")
    private Long reviewerUserId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", columnDefinition = "VARCHAR(1000)")
    private String reviewNote;
}
