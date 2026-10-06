package com.example.trainingsystems.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "research_samples",
    uniqueConstraints = @UniqueConstraint(name = "uq_research_sample_client",
        columnNames = {"participant_user_id", "client_sample_id"}),
    indexes = @Index(name = "idx_research_sample_participant", columnList = "participant_user_id,captured_at"))
@Data
public class ResearchSampleEntity {
    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "participant_user_id", nullable = false)
    private Long participantUserId;

    @Column(name = "subject_id", nullable = false, length = 36)
    private String subjectId;

    @Column(name = "client_sample_id", nullable = false, length = 100)
    private String clientSampleId;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "movement_side", nullable = false, length = 8)
    private String movementSide;

    @Column(name = "camera_view", nullable = false, length = 8)
    private String cameraView;

    @Column(name = "payload_json", nullable = false, columnDefinition = "LONGTEXT")
    private String payloadJson;

    @Column(name = "client_payload_hash", nullable = false, length = 64)
    private String clientPayloadHash;

    @Column(name = "retention_policy_version", length = 64)
    private String retentionPolicyVersion;

    @Column(name = "expires_at")
    private Instant expiresAt;

    // Nullable for historical v1/v2 rows; no inference of legacy source.
    @Column(name="modality", length=8) private String modality;
    @Column(name="source", length=16) private String source;
    @Column(name="schema_version") private Integer schemaVersion;
    @Column(name="action_id", length=64) private String actionId;
    @Column(name="session_id", length=100) private String sessionId;
    @Column(name="attempt_id", length=100) private String attemptId;
    @Column(name="exercise_type", length=16) private String exerciseType;
    @Column(name="exercise_id", length=100) private String exerciseId;
    @Column(name="disposition", nullable=false, length=32) private String disposition="ACTIVE";
}
