-- CONTROLLED ONE-TIME MANUAL BOOTSTRAP TEMPLATE; not an app startup migration.
-- In a restricted SSMS session, replace NULL with the verified existing users.id
-- of the first manager. Do not commit a filled-in copy or paste credentials.
-- Requires sqlserver_migration_ml_research_authority.sql first.
DECLARE @TargetUserId BIGINT = NULL;
DECLARE @StudyId VARCHAR(64) = 'standing-knee-raise-v1';
IF @TargetUserId IS NULL THROW 51000, 'Set an explicit existing user ID before bootstrap', 1;

SET XACT_ABORT ON;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
BEGIN TRANSACTION;
IF NOT EXISTS (SELECT 1 FROM dbo.users WITH (UPDLOCK, HOLDLOCK) WHERE id = @TargetUserId)
    THROW 51001, 'Target user does not exist', 1;
IF EXISTS (SELECT 1 FROM dbo.research_grants WITH (UPDLOCK, HOLDLOCK)
           WHERE study_id = @StudyId AND can_manage = 1)
    THROW 51002, 'A research manager already exists for this study', 1;
IF EXISTS (SELECT 1 FROM dbo.research_grants WITH (UPDLOCK, HOLDLOCK)
           WHERE study_id = @StudyId AND user_id = @TargetUserId)
    THROW 51003, 'Target already has a research grant; review manually', 1;

INSERT INTO dbo.research_grants
    (user_id, study_id, can_annotate, can_review, can_manage, granted_by_user_id, updated_at)
VALUES (@TargetUserId, @StudyId, 0, 0, 1, NULL, SYSUTCDATETIME());
INSERT INTO dbo.research_grant_audit
    (study_id, actor_user_id, target_user_id, action, created_at)
VALUES (@StudyId, NULL, @TargetUserId, 'FIRST_MANAGER_BOOTSTRAP', SYSUTCDATETIME());
COMMIT TRANSACTION;
