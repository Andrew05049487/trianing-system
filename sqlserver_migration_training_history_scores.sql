/*
 * Non-destructive migration for whole-body and optional template scoring.
 * Existing rows remain readable because scores and template metadata are nullable.
 */

IF COL_LENGTH('dbo.training_history', 'body_score') IS NULL
BEGIN
    ALTER TABLE dbo.training_history ADD body_score DECIMAL(5, 2) NULL;
END;

IF COL_LENGTH('dbo.training_history', 'body_rep_scores') IS NULL
BEGIN
    ALTER TABLE dbo.training_history ADD body_rep_scores NVARCHAR(MAX) NULL;
END;

IF COL_LENGTH('dbo.training_history', 'template_score') IS NULL
BEGIN
    ALTER TABLE dbo.training_history ADD template_score DECIMAL(5, 2) NULL;
END;

IF COL_LENGTH('dbo.training_history', 'template_id') IS NULL
BEGIN
    ALTER TABLE dbo.training_history ADD template_id VARCHAR(160) NULL;
END;

IF COL_LENGTH('dbo.training_history', 'template_name') IS NULL
BEGIN
    ALTER TABLE dbo.training_history ADD template_name NVARCHAR(200) NULL;
END;

IF COL_LENGTH('dbo.training_history', 'template_valid_rep_count') IS NULL
BEGIN
    ALTER TABLE dbo.training_history
        ADD template_valid_rep_count INT NOT NULL
            CONSTRAINT DF_training_history_template_valid_rep_count
            DEFAULT (0) WITH VALUES;
END;

IF COL_LENGTH('dbo.training_history', 'template_rep_scores') IS NULL
BEGIN
    ALTER TABLE dbo.training_history ADD template_rep_scores NVARCHAR(MAX) NULL;
END;

IF COL_LENGTH('dbo.training_history', 'template_difference_summary') IS NULL
BEGIN
    ALTER TABLE dbo.training_history
        ADD template_difference_summary NVARCHAR(MAX) NULL;
END;
