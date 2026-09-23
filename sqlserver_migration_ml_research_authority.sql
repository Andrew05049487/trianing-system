-- Additive third-round research authority schema. Execute manually in a
-- reviewed SQL Server environment after sqlserver_migration_ml_research.sql.
-- This script creates no manager account and grants no privileges.

IF OBJECT_ID(N'dbo.research_grants', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.research_grants (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        user_id BIGINT NOT NULL,
        study_id VARCHAR(64) NOT NULL,
        can_annotate BIT NOT NULL,
        can_review BIT NOT NULL,
        can_manage BIT NOT NULL,
        granted_by_user_id BIGINT NULL,
        updated_at DATETIME2 NOT NULL
    );
END;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.research_grants') AND name = N'uq_research_grant_user_study')
    CREATE UNIQUE INDEX uq_research_grant_user_study ON dbo.research_grants(user_id, study_id);
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'fk_research_grant_user')
    ALTER TABLE dbo.research_grants ADD CONSTRAINT fk_research_grant_user FOREIGN KEY (user_id) REFERENCES dbo.users(id);

IF OBJECT_ID(N'dbo.research_review_requests', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.research_review_requests (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        user_id BIGINT NOT NULL,
        study_id VARCHAR(64) NOT NULL,
        status VARCHAR(16) NOT NULL,
        requested_at DATETIME2 NOT NULL,
        decided_at DATETIME2 NULL,
        decided_by_user_id BIGINT NULL
    );
END;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.research_review_requests') AND name = N'uq_research_review_request_user_study')
    CREATE UNIQUE INDEX uq_research_review_request_user_study ON dbo.research_review_requests(user_id, study_id);
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'fk_research_review_request_user')
    ALTER TABLE dbo.research_review_requests ADD CONSTRAINT fk_research_review_request_user FOREIGN KEY (user_id) REFERENCES dbo.users(id);

IF OBJECT_ID(N'dbo.research_grant_audit', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.research_grant_audit (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        study_id VARCHAR(64) NOT NULL,
        actor_user_id BIGINT NULL,
        target_user_id BIGINT NOT NULL,
        action VARCHAR(32) NOT NULL,
        created_at DATETIME2 NOT NULL
    );
END;
