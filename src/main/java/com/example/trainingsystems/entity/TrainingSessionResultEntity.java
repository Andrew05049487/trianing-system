package com.example.trainingsystems.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "training_session_results",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_training_session_results_session_id",
        columnNames = "session_id"
    ),
    indexes = @Index(
        name = "idx_training_results_patient_completed",
        columnList = "patient_id,completed_at"
    )
)
@Data
public class TrainingSessionResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    @Column(name = "exercise_type", nullable = false, length = 16)
    private String exerciseType;

    @Column(
        name = "exercise_id",
        nullable = false,
        length = 128,
        columnDefinition = "nvarchar(128)"
    )
    private String exerciseId;

    @Column(
        name = "exercise_name",
        nullable = false,
        length = 255,
        columnDefinition = "nvarchar(255)"
    )
    private String exerciseName;

    @Column(name = "completed_sets", nullable = false)
    private Integer completedSets;

    @Column(name = "completed_reps", nullable = false)
    private Integer completedReps;

    @Column(name = "target_sets", nullable = false)
    private Integer targetSets;

    @Column(name = "target_reps", nullable = false)
    private Integer targetReps;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "duration_seconds", nullable = false)
    private Long durationSeconds;

    @Column(name = "completion_status", nullable = false, length = 24)
    private String completionStatus;

    @Column(name = "score", nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void beforeInsert() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
