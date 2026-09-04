package com.example.trainingsystems.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(
    name = "exercise_assignments",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_exercise_assignment",
        columnNames = {"exercise_id", "patient_id"}
    ),
    indexes = {
        @Index(
            name = "idx_exercise_assignments_patient_active",
            columnList = "patient_id,is_active"
        ),
        @Index(
            name = "idx_exercise_assignments_therapist",
            columnList = "assigned_by_therapist_id"
        )
    }
)
@Data
public class ExerciseAssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by_therapist_id", nullable = false)
    private User assignedByTherapist;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @PrePersist
    public void beforeSave() {
        if (assignedAt == null) {
            assignedAt = Instant.now();
        }
    }
}
