-- Additive export audit, after base research migration; no exported bytes on server.
IF OBJECT_ID(N'dbo.research_export_audit', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.research_export_audit (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        actor_user_id BIGINT NOT NULL,
        study_id VARCHAR(64) NOT NULL,
        schema_version INT NOT NULL,
        sample_count INT NOT NULL,
        created_at DATETIME2 NOT NULL
    );
END;
