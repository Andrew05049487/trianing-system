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
@Table(name = "research_grant_audit")
@Data
public class ResearchGrantAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_id", nullable = false, length = 64)
    private String studyId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
