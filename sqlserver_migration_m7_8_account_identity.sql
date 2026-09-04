/* Milestone 7.8 - nullable, case-insensitive Account ID identity.
   Run manually in SSMS against the production database. */

IF COL_LENGTH('dbo.users', 'account_id') IS NULL
BEGIN
    ALTER TABLE dbo.users ADD account_id varchar(20) NULL;
END;
GO

/* A persisted normalized column keeps uniqueness case-insensitive even when
   the database/table collation itself is case-sensitive. Dynamic SQL avoids
   SQL Server compiling a reference to a column added in the same deployment. */
IF COL_LENGTH('dbo.users', 'account_id_normalized') IS NULL
BEGIN
    EXEC(N'ALTER TABLE dbo.users ADD account_id_normalized AS LOWER(account_id) PERSISTED;');
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'uq_users_account_id_normalized'
      AND object_id = OBJECT_ID(N'dbo.users')
)
BEGIN
    EXEC(N'CREATE UNIQUE INDEX uq_users_account_id_normalized
           ON dbo.users (account_id_normalized)
           WHERE account_id IS NOT NULL;');
END;
GO
