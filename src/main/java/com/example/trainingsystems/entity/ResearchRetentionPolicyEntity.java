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
@Table(name = "research_retention_policies",
    uniqueConstraints = @UniqueConstraint(name = "uq_research_retention_policy_version",
        columnNames = {"study_id", "policy_version"}))
@Data
public class ResearchRetentionPolicyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "study_id", nullable = false, length = 64)
    private String studyId;
    @Column(name = "policy_version", nullable = false, length = 64)
    private String policyVersion;
    @Column(name = "retention_days", nullable = false)
    private int retentionDays;
    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;
    @Column(name = "configured_by_user_id", nullable = false)
    private Long configuredByUserId;
    @Column(name = "approval_reference", nullable = false, length = 128)
    private String approvalReference;
    @Column(name = "expiry_action", nullable = false, length = 16)
    private String expiryAction = "DELETE";
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
