package com.weedrice.whiteboard;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PushDeliveryJobMigrationContractTest {

    @Test
    void migrationCreatesDurablePerSubscriptionDeliveryContract() throws IOException {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V74__durable_web_push_delivery_jobs.sql"));

        assertThat(sql).contains("-- noviis:migration-phase expand");
        assertThat(sql).contains("CREATE TABLE push_delivery_jobs");
        assertThat(sql).contains("UNIQUE (event_id, subscription_id)");
        assertThat(sql).contains("status, next_attempt_at, job_id");
        assertThat(sql).contains("'PENDING', 'PROCESSING', 'COMPLETED', 'EXPIRED', 'FAILED'");
    }
}
