package com.weedrice.whiteboard;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationFollowupContractTest {

    @Test
    void v62AuditsRawLegacyReferencesBeforeBackfill() throws Exception {
        String sql = migration("V62__audit_legacy_scheduled_post_files.sql");

        assertThat(sql)
                .contains("migration-phase: backfill")
                .contains("legacy_scheduled_post_file_refs_raw")
                .contains("invalid_reference_count")
                .contains("ON COMMIT DROP");
    }

    @Test
    void v63DefinesDurableNotificationLeaseAndRetryState() throws Exception {
        String sql = migration("V63__notification_delivery_jobs.sql");

        assertThat(sql)
                .contains("migration-phase: expand")
                .contains("notification_delivery_jobs")
                .contains("processing_started_at")
                .contains("next_attempt_at")
                .contains("FAILED");
    }

    @Test
    void v64AddsVariantUploadIntentState() throws Exception {
        String sql = migration("V64__file_variant_upload_state.sql");

        assertThat(sql)
                .contains("migration-phase: expand")
                .contains("PENDING_UPLOAD")
                .contains("PENDING_DELETE")
                .contains("idx_file_variants_storage_status_created");
    }

    @Test
    void v87KeepsBoardListedVisibilityCompatibleWithPreviousWriters() throws Exception {
        String sql = migration("V87__add_board_listed_visibility.sql");

        assertThat(sql)
                .contains("-- noviis:migration-phase expand")
                .contains("ADD COLUMN is_listed varchar(1)")
                .contains("CASE WHEN is_public = 'N' THEN 'N' ELSE 'Y' END")
                .doesNotContain("DEFAULT 'Y'")
                .doesNotContain("ALTER COLUMN is_listed SET NOT NULL");
    }

    private String migration(String fileName) throws Exception {
        return new ClassPathResource("db/migration/" + fileName)
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
