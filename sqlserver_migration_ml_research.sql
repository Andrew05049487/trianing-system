-- Additive SQL Server migration. Review and execute in SSMS before deploying
-- the research-enabled backend. No user data, passwords or connection strings.
-- No ON DELETE CASCADE: AccountService explicitly cleans research rows.

IF OBJECT_ID(N'dbo.research_consents', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.research_consents (
        user_id BIGINT NOT NULL PRIMARY KEY,
        subject_id VARCHAR(36) NOT NULL,
        consent_version VARCHAR(64) NOT NULL,
        active BIT NOT NULL,
        consented_at DATETIME2 NULL,
        withdrawn_at DATETIME2 NULL
    );
END;

IF OBJECT_ID(N'dbo.research_samples', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.research_samples (
        id VARCHAR(36) NOT NULL PRIMARY KEY,
        participant_user_id BIGINT NOT NULL,
        subject_id VARCHAR(36) NOT NULL,
        client_sample_id VARCHAR(100) NOT NULL,
        captured_at DATETIME2 NOT NULL,
        uploaded_at DATETIME2 NOT NULL,
        movement_side VARCHAR(8) NOT NULL,
        camera_view VARCHAR(8) NOT NULL,
        payload_json NVARCHAR(MAX) NOT NULL,
        client_payload_hash VARCHAR(64) NOT NULL
    );
END;

IF OBJECT_ID(N'dbo.research_annotations', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.research_annotations (
        sample_id VARCHAR(36) NOT NULL PRIMARY KEY,
        therapist_user_id BIGINT NOT NULL,
        label VARCHAR(32) NOT NULL,
        note NVARCHAR(1000) NULL,
        label_version VARCHAR(64) NOT NULL,
        action_definition_version VARCHAR(64) NOT NULL,
        status VARCHAR(16) NOT NULL,
        updated_at DATETIME2 NOT NULL
    );
END;

IF OBJECT_ID(N'dbo.research_audit', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.research_audit (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        actor_user_id BIGINT NOT NULL,
        action VARCHAR(32) NOT NULL,
        sample_id VARCHAR(36) NULL,
        created_at DATETIME2 NOT NULL
    );
END;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.research_consents') AND name = N'uq_research_subject')
    CREATE UNIQUE INDEX uq_research_subject ON dbo.research_consents(subject_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.research_samples') AND name = N'uq_research_sample_client')
    CREATE UNIQUE INDEX uq_research_sample_client ON dbo.research_samples(participant_user_id, client_sample_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.research_samples') AND name = N'idx_research_sample_participant')
    CREATE INDEX idx_research_sample_participant ON dbo.research_samples(participant_user_id, captured_at);

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'fk_research_consent_user')
    ALTER TABLE dbo.research_consents ADD CONSTRAINT fk_research_consent_user
        FOREIGN KEY (user_id) REFERENCES dbo.users(id);
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'fk_research_sample_user')
    ALTER TABLE dbo.research_samples ADD CONSTRAINT fk_research_sample_user
        FOREIGN KEY (participant_user_id) REFERENCES dbo.users(id);
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'fk_research_annotation_sample')
    ALTER TABLE dbo.research_annotations ADD CONSTRAINT fk_research_annotation_sample
        FOREIGN KEY (sample_id) REFERENCES dbo.research_samples(id);
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'fk_research_annotation_therapist')
    ALTER TABLE dbo.research_annotations ADD CONSTRAINT fk_research_annotation_therapist
        FOREIGN KEY (therapist_user_id) REFERENCES dbo.users(id);
