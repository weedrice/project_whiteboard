package com.weedrice.whiteboard;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PushDeliveryJobRedactionMigrationContractTest {

    @Test
    void migrationAllowsAndBackfillsTerminalSnapshotRedaction() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V75__redact_terminal_push_delivery_jobs.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).contains("-- noviis:migration-phase expand")
                .contains("ALTER COLUMN endpoint DROP NOT NULL")
                .contains("ALTER COLUMN payload DROP NOT NULL")
                .contains("WHERE status IN ('COMPLETED', 'EXPIRED', 'FAILED')")
                .contains("idx_push_delivery_jobs_receiver_status");
    }
}
