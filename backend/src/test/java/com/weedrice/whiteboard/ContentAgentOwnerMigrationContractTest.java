package com.weedrice.whiteboard;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ContentAgentOwnerMigrationContractTest {

    @Test
    void v97CreatesOnlineUniqueAgentOwnerKeyOutsideTransaction() throws Exception {
        String sql = migration("V97__index_agent_owner_pair.sql");
        String configuration = migration("V97__index_agent_owner_pair.sql.conf");

        assertThat(sql)
                .contains("-- noviis:migration-phase expand")
                .contains("-- noviis:online-index uk_agents_agent_owner")
                .contains("CREATE UNIQUE INDEX CONCURRENTLY uk_agents_agent_owner")
                .contains("ON agents (agent_id, user_id)");
        assertThat(configuration.trim()).isEqualTo("executeInTransaction=false");
    }

    @Test
    void v98AddsNonValidCompositeForeignKeysWithoutRemovingLegacyKeys() throws Exception {
        String sql = migration("V98__add_content_agent_owner_foreign_keys.sql");

        assertThat(sql)
                .contains("CONSTRAINT fk_posts_agent_owner")
                .contains("CONSTRAINT fk_comments_agent_owner")
                .contains("FOREIGN KEY (agent_id, user_id)")
                .contains("REFERENCES agents (agent_id, user_id)")
                .contains("NOT VALID")
                .doesNotContain("DROP CONSTRAINT")
                .doesNotContain("DROP COLUMN");
    }

    @Test
    void v99ValidatesBothCompositeForeignKeys() throws Exception {
        String sql = migration("V99__validate_content_agent_owner_foreign_keys.sql");

        assertThat(sql)
                .contains("VALIDATE CONSTRAINT fk_posts_agent_owner")
                .contains("VALIDATE CONSTRAINT fk_comments_agent_owner");
    }

    private String migration(String fileName) throws Exception {
        return new ClassPathResource("db/migration/" + fileName)
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
