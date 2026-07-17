package com.weedrice.whiteboard;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FeedGenerationRetryMigrationContractTest {

    @Test
    void v73AddsDueScheduleAndOnlineIndexContract() throws Exception {
        String sql = new ClassPathResource("db/migration/V73__feed_generation_retry_schedule.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        String config = new ClassPathResource("db/migration/V73__feed_generation_retry_schedule.sql.conf")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql)
                .contains("-- noviis:migration-phase expand")
                .contains("SET lock_timeout = '5s'")
                .contains("ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP(6)")
                .contains("-- noviis:online-index idx_feed_generation_jobs_due")
                .contains("CREATE INDEX CONCURRENTLY idx_feed_generation_jobs_due")
                .contains("status, next_attempt_at, created_at, job_id");
        assertThat(config.trim()).isEqualTo("executeInTransaction=false");
    }
}
