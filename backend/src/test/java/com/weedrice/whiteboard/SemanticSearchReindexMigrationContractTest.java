package com.weedrice.whiteboard;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticSearchReindexMigrationContractTest {

    @Test
    void v71AddsRetryLeaseAndDeadLetterContract() throws IOException {
        String sql;
        try (var input = getClass().getClassLoader().getResourceAsStream(
                "db/migration/V71__semantic_reindex_retry_state.sql")) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("-- noviis:migration-phase contract")
                .contains("retry_count INTEGER NOT NULL DEFAULT 0")
                .contains("next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP")
                .contains("last_error VARCHAR(500)")
                .contains("lease_token UUID")
                .contains("'FAILED'")
                .contains("idx_semantic_search_reindex_runnable");
    }
}

