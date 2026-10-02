-- R3 additive migration, apply once to the already validated V001+V002 schema.
-- Old rows remain NULL; application reads NULL as an empty rules array.
ALTER TABLE custom_rehab_exercises
    ADD COLUMN pose_measurement_rules_json LONGTEXT NULL;
