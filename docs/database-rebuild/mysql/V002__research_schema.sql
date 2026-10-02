-- Optional research FINAL schema: apply ONLY after V001 in the same schema.
-- Consolidates base/authority/review/export/retention; 11 NEW tables.
-- No accounts, grants, consent, labels, policies, or data collection enabled here.
-- Run once without --force; fail on existing tables instead of hiding drift.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_as_cs;
SET SESSION time_zone = '+00:00';

CREATE TABLE research_consents (
    user_id BIGINT NOT NULL,
    subject_id VARCHAR(36) NOT NULL,
    consent_version VARCHAR(64) NOT NULL,
    active TINYINT(1) NOT NULL,
    consented_at DATETIME(6) NULL,
    withdrawn_at DATETIME(6) NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY uq_research_consent_subject (subject_id),
    CONSTRAINT fk_research_consent_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_research_consent_active CHECK (active IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE research_samples (
    id VARCHAR(36) NOT NULL,
    participant_user_id BIGINT NOT NULL,
    subject_id VARCHAR(36) NOT NULL,
    client_sample_id VARCHAR(100) NOT NULL,
    captured_at DATETIME(6) NOT NULL,
    uploaded_at DATETIME(6) NOT NULL,
    movement_side VARCHAR(8) NOT NULL,
    camera_view VARCHAR(8) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    client_payload_hash VARCHAR(64) NOT NULL,
    retention_policy_version VARCHAR(64) NULL,
    expires_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_research_sample_client (participant_user_id, client_sample_id),
    KEY idx_research_sample_participant (participant_user_id, captured_at),
    KEY idx_research_sample_expiry (expires_at),
    CONSTRAINT fk_research_sample_participant FOREIGN KEY (participant_user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE research_annotations (
    sample_id VARCHAR(36) NOT NULL,
    therapist_user_id BIGINT NOT NULL,
    label VARCHAR(32) NOT NULL,
    note VARCHAR(1000) NULL,
    label_version VARCHAR(64) NOT NULL,
    action_definition_version VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    revision INT NOT NULL DEFAULT 0,
    submitted_at DATETIME(6) NULL,
    reviewer_user_id BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    review_note VARCHAR(1000) NULL,
    PRIMARY KEY (sample_id),
    CONSTRAINT fk_research_annotation_sample FOREIGN KEY (sample_id) REFERENCES research_samples(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_research_annotation_therapist FOREIGN KEY (therapist_user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE research_annotation_revisions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sample_id VARCHAR(36) NOT NULL,
    revision INT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    annotator_user_id BIGINT NOT NULL,
    label VARCHAR(32) NOT NULL,
    note VARCHAR(1000) NULL,
    label_version VARCHAR(64) NOT NULL,
    action_definition_version VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    reviewer_user_id BIGINT NULL,
    review_note VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- Original migration declares an ordinary index, NOT a unique key.
    KEY idx_research_annotation_revision_sample (sample_id, revision),
    CONSTRAINT fk_research_annotation_revision_sample FOREIGN KEY (sample_id) REFERENCES research_samples(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE research_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    sample_id VARCHAR(36) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE research_grants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    study_id VARCHAR(64) NOT NULL,
    can_annotate TINYINT(1) NOT NULL,
    can_review TINYINT(1) NOT NULL,
    can_manage TINYINT(1) NOT NULL,
    granted_by_user_id BIGINT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_research_grant_user_study (user_id, study_id),
    CONSTRAINT fk_research_grant_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_research_grant_annotate CHECK (can_annotate IN (0,1)),
    CONSTRAINT ck_research_grant_review CHECK (can_review IN (0,1)),
    CONSTRAINT ck_research_grant_manage CHECK (can_manage IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE research_review_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    study_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    decided_at DATETIME(6) NULL,
    decided_by_user_id BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_research_review_request_user_study (user_id, study_id),
    CONSTRAINT fk_research_review_request_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE research_grant_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    study_id VARCHAR(64) NOT NULL,
    actor_user_id BIGINT NULL,
    target_user_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE research_export_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT NOT NULL,
    study_id VARCHAR(64) NOT NULL,
    schema_version INT NOT NULL,
    sample_count INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE research_retention_policies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    study_id VARCHAR(64) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    retention_days INT NOT NULL,
    effective_at DATETIME(6) NOT NULL,
    configured_by_user_id BIGINT NOT NULL,
    approval_reference VARCHAR(128) NOT NULL,
    expiry_action VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_research_retention_policy_version (study_id, policy_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE research_retention_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sample_id VARCHAR(36) NOT NULL,
    policy_version VARCHAR(64) NULL,
    reason VARCHAR(24) NOT NULL,
    actor_user_id BIGINT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
