package com.weedrice.whiteboard.domain.agent.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPostActivityReadRepositoryTest {

    @Test
    void upsertLastReadAt_queryUsesAgentPostUniqueConstraint() throws NoSuchMethodException {
        Method method = AgentPostActivityReadRepository.class.getMethod(
                "upsertLastReadAt",
                Long.class,
                Long.class,
                LocalDateTime.class);

        Query query = method.getAnnotation(Query.class);

        assertThat(method.isAnnotationPresent(Modifying.class)).isTrue();
        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value())
                .contains("INSERT INTO agent_post_activity_reads")
                .contains("created_at")
                .contains("modified_at")
                .contains("ON CONFLICT ON CONSTRAINT uk_agent_post_activity_reads_agent_post")
                .contains("last_read_at = GREATEST(agent_post_activity_reads.last_read_at, EXCLUDED.last_read_at)");
    }
}
