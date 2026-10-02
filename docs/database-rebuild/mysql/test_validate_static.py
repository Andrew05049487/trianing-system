"""Mutation tests for the read-only checker; no database or on-disk mutation."""
import unittest
from pathlib import Path
from unittest.mock import patch
import validate_static as audit


class StaticAuditTests(unittest.TestCase):
    def mutate(self, filename, old, new, checker=audit.parse):
        read = Path.read_text

        def changed(path, *args, **kwargs):
            text = read(path, *args, **kwargs)
            if path.name == filename:
                self.assertIn(old, text)
                return text.replace(old, new, 1)
            return text

        with patch.object(Path, "read_text", changed):
            with self.assertRaises(AssertionError):
                checker()

    def test_complete_schema_and_entities(self):
        tables, cols, indexes, fks, checks = audit.parse()
        self.assertEqual((len(tables), len(cols), len(indexes), len(fks), len(checks)), (29, 240, 68, 33, 16))
        self.assertEqual(audit.audit_entities(cols, indexes), 238)

    def test_validation_manifest_is_current(self):
        self.assertEqual((audit.HERE / "validate_schema.sql").read_text(encoding="utf-8"), audit.validation_sql(audit.parse()))

    def test_check_metadata_delimiters_normalized_without_folding_literal_case(self):
        sql = audit.validation_sql(audit.parse())
        clause = sql[sql.index("WHERE a.CONSTRAINT_NAME IS NULL OR tc.ENFORCED"):]
        clause = clause.split(";", 1)[0]
        self.assertEqual(clause.count("CONCAT(CHAR(92),CHAR(39)),CHAR(39)"), 2)
        self.assertNotIn("LOWER(", clause)

    def test_missing_table_fails(self):
        self.mutate("V001__main_schema.sql", "CREATE TABLE exercise (", "CREATE TABLE renamed_exercise (")

    def test_duplicate_column_fails(self):
        self.mutate("V001__main_schema.sql", "goal VARCHAR(255) NULL", "name VARCHAR(255) NULL")

    def test_missing_fk_fails(self):
        self.mutate("V001__main_schema.sql", "CONSTRAINT fk_training_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT", "KEY not_a_foreign_key (user_id)")

    def test_fk_collation_mismatch_fails(self):
        self.mutate("V001__main_schema.sql", "custom_exercise_id VARCHAR(128) NOT NULL", "custom_exercise_id VARCHAR(128) COLLATE utf8mb4_0900_as_ci NOT NULL")

    def test_stored_reset_generated_column_fails(self):
        self.mutate("V001__main_schema.sql", "ELSE NULL END) VIRTUAL", "ELSE NULL END) STORED")

    def test_invented_default_fails(self):
        self.mutate("V001__main_schema.sql", "role VARCHAR(20) NOT NULL", "role VARCHAR(20) NOT NULL DEFAULT 0")

    def test_revision_index_must_not_be_unique(self):
        self.mutate("V002__research_schema.sql", "KEY idx_research_annotation_revision_sample", "UNIQUE KEY idx_research_annotation_revision_sample")

    def test_removed_check_fails(self):
        self.mutate("V001__main_schema.sql", "CONSTRAINT ck_training_video_size CHECK (file_size > 0)", "KEY replacement_check (file_size)")

    def test_tsql_fails(self):
        self.mutate("V001__main_schema.sql", "password VARCHAR(255) NULL", "password NVARCHAR(255) NULL")

    def test_entity_length_drift_fails(self):
        self.mutate("User.java", "length = 50", "length = 20", lambda: audit.audit_entities(audit.parse()[1], audit.parse()[2]))

    def test_entity_unique_drift_fails(self):
        self.mutate("Friendship.java", '"user_low_id", "user_high_id"', '"user_high_id", "user_low_id"', lambda: audit.audit_entities(audit.parse()[1], audit.parse()[2]))


if __name__ == "__main__":
    unittest.main(verbosity=2)
