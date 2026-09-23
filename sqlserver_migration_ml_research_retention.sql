-- Additive retention schema. Review/backup and run manually AFTER base research migration.
-- No retention duration is supplied by this script. No production rows are deleted here.
IF OBJECT_ID(N'dbo.research_retention_policies', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.research_retention_policies (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        study_id VARCHAR(64) NOT NULL,
        policy_version VARCHAR(64) NOT NULL,
        retention_days INT NOT NULL,
        effective_at DATETIME2 NOT NULL,
        configured_by_user_id BIGINT NOT NULL,
        approval_reference VARCHAR(128) NOT NULL,
        expiry_action VARCHAR(16) NOT NULL,
        created_at DATETIME2 NOT NULL
    );
END;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.research_retention_policies') AND name = N'uq_research_retention_policy_version')
    CREATE UNIQUE INDEX uq_research_retention_policy_version
        ON dbo.research_retention_policies(study_id, policy_version);

IF OBJECT_ID(N'dbo.research_retention_events', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.research_retention_events (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        sample_id VARCHAR(36) NOT NULL,
        policy_version VARCHAR(64) NULL,
        reason VARCHAR(24) NOT NULL,
        actor_user_id BIGINT NULL,
        processed_at DATETIME2 NOT NULL
    );
END;

IF COL_LENGTH('dbo.research_samples', 'retention_policy_version') IS NULL
    ALTER TABLE dbo.research_samples ADD retention_policy_version VARCHAR(64) NULL;
IF COL_LENGTH('dbo.research_samples', 'expires_at') IS NULL
    ALTER TABLE dbo.research_samples ADD expires_at DATETIME2 NULL;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.research_samples') AND name = N'idx_research_sample_expiry')
    CREATE INDEX idx_research_sample_expiry ON dbo.research_samples(expires_at)
        WHERE expires_at IS NOT NULL;
