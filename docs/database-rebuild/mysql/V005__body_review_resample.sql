-- Round 3 additive review metadata. Apply after V004 in isolated development.
-- Original skeleton payload is immutable. Parent deletion keeps child sample.
ALTER TABLE research_samples
  ADD COLUMN resample_of_sample_id VARCHAR(36) NULL,
  ADD CONSTRAINT fk_research_resample_parent FOREIGN KEY (resample_of_sample_id)
    REFERENCES research_samples(id) ON DELETE SET NULL,
  ADD INDEX idx_research_resample_parent (resample_of_sample_id);
ALTER TABLE research_annotations ADD COLUMN reason_code VARCHAR(64) NULL;
ALTER TABLE research_annotation_revisions
  ADD COLUMN reason_code VARCHAR(64) NULL,
  ADD COLUMN disposition VARCHAR(32) NULL;
