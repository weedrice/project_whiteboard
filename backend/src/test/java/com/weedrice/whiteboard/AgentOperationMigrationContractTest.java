package com.weedrice.whiteboard;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class AgentOperationMigrationContractTest {
    @Test
    void receiptMigrationIsAdditiveAndScopesKeysToAgentIdentity() throws Exception {
        String sql = new ClassPathResource("db/migration/V101__agent_write_operations.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(sql).contains("-- noviis:migration-phase expand", "PRIMARY KEY (agent_id, operation_id)",
                "REFERENCES agents(agent_id)", "request_digest CHAR(64)", "completed_at TIMESTAMP WITH TIME ZONE")
                .doesNotContain("DROP ", "DELETE ", "ALTER TABLE", "agent_token", "request_body");
    }
}
