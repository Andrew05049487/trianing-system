-- Apply after sqlserver_migration_ml_research.sql. Additive; no automatic approvals.
IF COL_LENGTH('dbo.research_annotations', 'revision') IS NULL
    ALTER TABLE dbo.research_annotations ADD revision INT NOT NULL CONSTRAINT df_research_annotation_revision DEFAULT (0);
IF COL_LENGTH('dbo.research_annotations', 'submitted_at') IS NULL
    ALTER TABLE dbo.research_annotations ADD submitted_at DATETIME2 NULL;
IF COL_LENGTH('dbo.research_annotations', 'reviewer_user_id') IS NULL
    ALTER TABLE dbo.research_annotations ADD reviewer_user_id BIGINT NULL;
IF COL_LENGTH('dbo.research_annotations', 'reviewed_at') IS NULL
    ALTER TABLE dbo.research_annotations ADD reviewed_at DATETIME2 NULL;
IF COL_LENGTH('dbo.research_annotations', 'review_note') IS NULL
    ALTER TABLE dbo.research_annotations ADD review_note NVARCHAR(1000) NULL;

-- Second-round LABELED rows remain unapproved drafts, never eligible for export.
UPDATE dbo.research_annotations SET status = 'DRAFT' WHERE status = 'LABELED';

IF OBJECT_ID(N'dbo.research_annotation_revisions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.research_annotation_revisions (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        sample_id VARCHAR(36) NOT NULL,
        revision INT NOT NULL,
        actor_user_id BIGINT NOT NULL,
        annotator_user_id BIGINT NOT NULL,
        label VARCHAR(32) NOT NULL,
        note NVARCHAR(1000) NULL,
        label_version VARCHAR(64) NOT NULL,
        action_definition_version VARCHAR(64) NOT NULL,
        status VARCHAR(16) NOT NULL,
        reviewer_user_id BIGINT NULL,
        review_note NVARCHAR(1000) NULL,
        created_at DATETIME2 NOT NULL
    );
END;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.research_annotation_revisions') AND name = N'idx_research_annotation_revision_sample')
    CREATE INDEX idx_research_annotation_revision_sample ON dbo.research_annotation_revisions(sample_id, revision);
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'fk_research_annotation_revision_sample')
    ALTER TABLE dbo.research_annotation_revisions ADD CONSTRAINT fk_research_annotation_revision_sample
        FOREIGN KEY (sample_id) REFERENCES dbo.research_samples(id);
