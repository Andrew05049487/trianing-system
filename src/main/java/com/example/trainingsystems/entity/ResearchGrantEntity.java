package com.example.trainingsystems.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.Instant;

/** Additive research privileges; never replaces User.role. */
@Entity
@Table(name = "research_grants", uniqueConstraints =
    @UniqueConstraint(name = "uq_research_grant_user_study", columnNames = {"user_id", "study_id"}))
@Data
public class ResearchGrantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "study_id", nullable = false, length = 64)
    private String studyId;

    @Column(name = "can_annotate", nullable = false)
    private boolean canAnnotate;

    @Column(name = "can_review", nullable = false)
    private boolean canReview;

    @Column(name = "can_manage", nullable = false)
    private boolean canManage;

    @Column(name = "granted_by_user_id")
    private Long grantedByUserId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
