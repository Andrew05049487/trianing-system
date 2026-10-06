package com.example.trainingsystems.service;

import com.example.trainingsystems.entity.*;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

/** Static compatibility checks, NOT a substitute for live MySQL validation. */
class ResearchBodyMigrationTest {
    @Test void migrationIsAdditiveAndLegacyColumnsNullable() throws Exception {
        String ddl=Files.readString(Path.of("docs/database-rebuild/mysql/V004__body_attempt_research.sql"));
        assertThat(ddl).contains("ALTER TABLE research_samples","ALTER TABLE research_annotations",
            "ALTER TABLE research_annotation_revisions","ALTER TABLE research_audit",
            "schema_version IS NULL OR","modality IS NOT NULL","source IS NOT NULL",
            "action_id IS NOT NULL","exercise_type IS NOT NULL",
            "UNIQUE (participant_user_id, attempt_id)","DEFAULT 'ACTIVE'");
        assertThat(ddl.toUpperCase()).doesNotContain("DROP TABLE","TRUNCATE","DELETE FROM",
            "CREATE TABLE","FOREIGN_KEY_CHECKS","UPDATE RESEARCH");
        assertThat(ResearchSampleEntity.class.getDeclaredField("schemaVersion")
            .getAnnotation(Column.class).nullable()).isTrue();
    }
    @Test void reviewerVersionContextDoesNotRequireLegacyBackfill() throws Exception {
        for(Class<?> entity:new Class<?>[]{ResearchAnnotationEntity.class,
            ResearchAnnotationRevisionEntity.class,ResearchAuditEntity.class}) {
            var field=entity.getDeclaredField("schemaVersion");
            assertThat(field.getType()).isEqualTo(Integer.class);
            assertThat(field.getAnnotation(Column.class).nullable()).isTrue();
        }
    }
}
