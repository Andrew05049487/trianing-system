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

@Entity
@Table(name = "research_review_requests", uniqueConstraints =
    @UniqueConstraint(name = "uq_research_review_request_user_study", columnNames = {"user_id", "study_id"}))
@Data
public class ResearchReviewRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "study_id", nullable = false, length = 64)
    private String studyId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by_user_id")
    private Long decidedByUserId;
}
