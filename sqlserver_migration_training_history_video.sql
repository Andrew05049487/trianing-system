/*
  Safe, repeatable SQL Server migration for training history completion counts
  and durable video storage. This script never drops training_history and does
  not modify uq_training_history_user_ts.
*/
SET XACT_ABORT ON;
SET NOCOUNT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.training_history', N'U') IS NULL
        THROW 51000, 'dbo.training_history does not exist.', 1;

    IF COL_LENGTH(N'dbo.training_history', N'action_name') IS NULL
        THROW 51001, 'dbo.training_history.action_name does not exist.', 1;

    IF EXISTS (
        SELECT 1
        FROM dbo.training_history
        WHERE action_name IS NULL
    )
        THROW 51002, 'action_name contains NULL; repair those rows before migration.', 1;

    IF EXISTS (
        SELECT 1
        FROM sys.columns c
        JOIN sys.types t ON t.user_type_id = c.user_type_id
        WHERE c.object_id = OBJECT_ID(N'dbo.training_history')
          AND c.name = N'action_name'
          AND (t.name <> N'nvarchar' OR c.max_length <> 200 OR c.is_nullable <> 0)
    )
    BEGIN
        ALTER TABLE dbo.training_history
            ALTER COLUMN action_name NVARCHAR(100) NOT NULL;
    END;

    IF COL_LENGTH(N'dbo.training_history', N'mistake_logs') IS NULL
        THROW 51003, 'dbo.training_history.mistake_logs does not exist.', 1;

    IF EXISTS (
        SELECT 1
        FROM sys.columns c
        JOIN sys.types t ON t.user_type_id = c.user_type_id
        WHERE c.object_id = OBJECT_ID(N'dbo.training_history')
          AND c.name = N'mistake_logs'
          AND (t.name <> N'nvarchar' OR c.max_length <> -1)
    )
    BEGIN
        ALTER TABLE dbo.training_history
            ALTER COLUMN mistake_logs NVARCHAR(MAX) NULL;
    END;

    IF COL_LENGTH(N'dbo.training_history', N'completed_reps') IS NULL
    BEGIN
        ALTER TABLE dbo.training_history
            ADD completed_reps INT NOT NULL
                CONSTRAINT DF_training_history_completed_reps DEFAULT (0)
                WITH VALUES;
    END
    ELSE
    BEGIN
        UPDATE dbo.training_history
        SET completed_reps = 0
        WHERE completed_reps IS NULL;

        ALTER TABLE dbo.training_history
            ALTER COLUMN completed_reps INT NOT NULL;

        IF NOT EXISTS (
            SELECT 1
            FROM sys.default_constraints dc
            JOIN sys.columns c
              ON c.object_id = dc.parent_object_id
             AND c.column_id = dc.parent_column_id
            WHERE dc.parent_object_id = OBJECT_ID(N'dbo.training_history')
              AND c.name = N'completed_reps'
        )
        BEGIN
            ALTER TABLE dbo.training_history
                ADD CONSTRAINT DF_training_history_completed_reps
                DEFAULT (0) FOR completed_reps;
        END;
    END;

    IF OBJECT_ID(N'dbo.training_history_video', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.training_history_video (
            history_id BIGINT NOT NULL,
            file_name NVARCHAR(255) NULL,
            content_type NVARCHAR(100) NOT NULL,
            file_size BIGINT NOT NULL,
            video_data VARBINARY(MAX) NOT NULL,
            created_at DATETIME2 NOT NULL,
            updated_at DATETIME2 NULL,
            CONSTRAINT PK_training_history_video PRIMARY KEY (history_id),
            CONSTRAINT CK_training_history_video_file_size
                CHECK (file_size > 0),
            CONSTRAINT FK_training_history_video_history
                FOREIGN KEY (history_id)
                REFERENCES dbo.training_history(id)
                ON DELETE CASCADE
        );
    END;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.training_history')
          AND name = N'IX_training_history_user_created'
    )
    BEGIN
        CREATE INDEX IX_training_history_user_created
            ON dbo.training_history(user_id, created_at DESC);
    END;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

/* Verification queries (intentionally omit video_data). */
SELECT
    c.name AS column_name,
    t.name AS sql_type,
    c.max_length,
    c.is_nullable
FROM sys.columns c
JOIN sys.types t ON t.user_type_id = c.user_type_id
WHERE c.object_id = OBJECT_ID(N'dbo.training_history')
  AND c.name IN (N'action_name', N'mistake_logs', N'completed_reps');

SELECT TOP (1000)
    [id], [action_name], [client_timestamp], [created_at], [difficulty],
    [duration_seconds], [completed_reps], [mistake_count], [mistake_logs],
    [target_reps], [user_id]
FROM [dbo].[training_history]
ORDER BY [id] DESC;

SELECT
    h.id, h.action_name, h.user_id, v.file_name, v.content_type,
    v.file_size, v.created_at
FROM dbo.training_history h
LEFT JOIN dbo.training_history_video v ON v.history_id = h.id
ORDER BY h.id DESC;
