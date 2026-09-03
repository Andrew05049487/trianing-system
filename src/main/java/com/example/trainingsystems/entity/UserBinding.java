package com.example.trainingsystems.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "user_bindings",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_user_binding",
            columnNames = {"patient_id", "linked_user_id"}
        )
    }
)
@Data
public class UserBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    @ManyToOne
    @JoinColumn(name = "linked_user_id", nullable = false)
    private User linkedUser;

    @Column(nullable = false, length = 20)
    private String relationship;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void beforeSave() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
