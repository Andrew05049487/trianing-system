/*
  Account Recovery additive SQL Server migration.
  Review and execute manually against production before deploying the backend.
*/

IF OBJECT_ID(N'dbo.password_reset_requests', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.password_reset_requests (
        id VARCHAR(36) NOT NULL,
        user_id BIGINT NOT NULL,
        code_hash VARCHAR(64) NOT NULL,
        expires_at DATETIME2(7) NOT NULL,
        failed_attempts INT NOT NULL CONSTRAINT df_password_reset_failed_attempts DEFAULT 0,
        consumed_at DATETIME2(7) NULL,
        created_at DATETIME2(7) NOT NULL,
        version BIGINT NULL,
        CONSTRAINT pk_password_reset_requests PRIMARY KEY (id),
        CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id)
            REFERENCES dbo.users(id) ON DELETE CASCADE
    );
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'ix_password_reset_user_created'
      AND object_id = OBJECT_ID(N'dbo.password_reset_requests')
)
BEGIN
    CREATE INDEX ix_password_reset_user_created
        ON dbo.password_reset_requests (user_id, created_at);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'ix_password_reset_expires_at'
      AND object_id = OBJECT_ID(N'dbo.password_reset_requests')
)
BEGIN
    CREATE INDEX ix_password_reset_expires_at
        ON dbo.password_reset_requests (expires_at);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'uq_password_reset_active_user'
      AND object_id = OBJECT_ID(N'dbo.password_reset_requests')
)
BEGIN
    CREATE UNIQUE INDEX uq_password_reset_active_user
        ON dbo.password_reset_requests (user_id)
        WHERE consumed_at IS NULL;
END;
