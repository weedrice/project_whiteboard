package com.weedrice.whiteboard;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordNotificationLookupMigrationContractTest {

    @Test
    void createsKeywordPostLookupIndexForAllDeliveryStates() throws Exception {
        String sql = migration("V101__index_keyword_notification_delivery_lookup.sql");

        assertThat(sql)
                .contains("-- noviis:migration-phase expand")
                .contains("ON notification_delivery_jobs (source_id, receiver_user_id)")
                .contains("WHERE notification_type = 'KEYWORD'")
                .contains("AND source_type = 'POST'")
                // Completed/failed legacy events still carry the original deduplication identity.
                .doesNotContain("status =", "status IN", "DELETE", "DROP", "ALTER TABLE");
    }

    @Test
    void buildsOnlineWithBoundedLockWaitOutsideTransaction() throws Exception {
        String sql = migration("V101__index_keyword_notification_delivery_lookup.sql");
        String configuration = migration("V101__index_keyword_notification_delivery_lookup.sql.conf");

        assertThat(sql)
                .contains("-- noviis:online-index idx_notification_delivery_jobs_keyword_post_receiver")
                .contains("CREATE INDEX CONCURRENTLY idx_notification_delivery_jobs_keyword_post_receiver")
                .contains("SET lock_timeout = '5s'")
                .contains("RESET lock_timeout")
                .doesNotContain("IF NOT EXISTS");
        assertThat(configuration.trim()).isEqualTo("executeInTransaction=false");
    }

    private String migration(String fileName) throws Exception {
        return new ClassPathResource("db/migration/" + fileName)
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
