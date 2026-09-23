package com.example.trainingsystems.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "research_consents")
@Data
public class ResearchConsentEntity {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "subject_id", nullable = false, unique = true, length = 36)
    private String subjectId;

    @Column(name = "consent_version", nullable = false, length = 64)
    private String consentVersion;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "consented_at")
    private Instant consentedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;
}
