-- Round 2 body attempt foundation. Apply once AFTER V001/V002/V003.
-- Never run automatically or against production as part of a build.
-- Legacy rows retain NULL version/source/context. No new sample table.
ALTER TABLE research_samples
  ADD COLUMN modality VARCHAR(8) NULL,
  ADD COLUMN source VARCHAR(16) NULL,
  ADD COLUMN schema_version INT NULL,
  ADD COLUMN action_id VARCHAR(64) NULL,
  ADD COLUMN session_id VARCHAR(100) NULL,
  ADD COLUMN attempt_id VARCHAR(100) NULL,
  ADD COLUMN exercise_type VARCHAR(16) NULL,
  ADD COLUMN exercise_id VARCHAR(100) NULL,
  ADD COLUMN disposition VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  ADD CONSTRAINT uq_research_sample_attempt UNIQUE (participant_user_id, attempt_id),
  ADD INDEX idx_research_sample_modality_source (modality,source,schema_version),
  ADD CONSTRAINT chk_research_sample_v3 CHECK (
    schema_version IS NULL OR
    (schema_version=3 AND modality IS NOT NULL AND modality='body'
      AND source IS NOT NULL AND source IN ('phone','tv_pi')
      AND action_id IS NOT NULL AND action_id='standing_knee_raise' AND session_id IS NOT NULL
      AND attempt_id IS NOT NULL AND exercise_type IS NOT NULL AND exercise_type IN ('DEFAULT','CUSTOM')
      AND exercise_id IS NOT NULL)),
  ADD CONSTRAINT chk_research_sample_disposition CHECK (
    disposition IN ('ACTIVE','EXCLUDED','REJECTED','NEEDS_RESAMPLE'));

-- Version context for future reviewer/audit consumers; no review-flow rewrite.
ALTER TABLE research_annotations ADD COLUMN schema_version INT NULL;
ALTER TABLE research_annotation_revisions ADD COLUMN schema_version INT NULL;
ALTER TABLE research_audit ADD COLUMN schema_version INT NULL;
