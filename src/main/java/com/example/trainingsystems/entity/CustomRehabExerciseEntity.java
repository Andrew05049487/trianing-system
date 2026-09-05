package com.example.trainingsystems.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Entity
@Table(
    name = "custom_rehab_exercises",
    indexes = @Index(
        name = "idx_custom_rehab_exercises_therapist",
        columnList = "created_by_therapist_id"
    )
)
@Data
public class CustomRehabExerciseEntity {

    @Id
    @Column(length = 128)
    private String id;

    @Column(nullable = false, length = 200)
    @Nationalized
    private String name;

    @Column(nullable = false, length = 2000)
    @Nationalized
    private String description = "";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_therapist_id", nullable = false)
    private User createdByTherapist;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private Integer repetitions;

    @Column(nullable = false)
    private Integer sets;

    @Column(name = "hold_seconds", nullable = false)
    private Double holdSeconds;

    @Column(name = "rest_seconds", nullable = false)
    private Double restSeconds;

    @Column(nullable = false)
    private Double duration;

    @Column(name = "keyframes_json", nullable = false, columnDefinition = "nvarchar(max)")
    private String keyframesJson;

    @Column(
        name = "evaluation_rules_json",
        nullable = false,
        columnDefinition = "nvarchar(max)"
    )
    private String evaluationRulesJson;

    @Column(
        name = "pose_measurement_rules_json",
        columnDefinition = "nvarchar(max)"
    )
    private String poseMeasurementRulesJson;
}
