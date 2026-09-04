/*
  Milestone 7.7 manual SQL Server migration.
  Run once against production before enabling Google authentication.
  This script is intentionally not executed automatically by Hibernate.
*/

IF COL_LENGTH(N'dbo.users', N'google_subject') IS NULL
BEGIN
    ALTER TABLE dbo.users ADD google_subject VARCHAR(255) NULL;
END;

/* Google-only accounts do not have a password. */
ALTER TABLE dbo.users ALTER COLUMN password VARCHAR(255) NULL;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'uq_users_google_subject'
      AND object_id = OBJECT_ID(N'dbo.users')
)
BEGIN
    CREATE UNIQUE INDEX uq_users_google_subject
        ON dbo.users (google_subject)
        WHERE google_subject IS NOT NULL;
END;
