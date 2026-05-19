package com.weedrice.whiteboard.domain.agent.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AgentNoteThreadRepositoryTest {

    @Test
    void insertIgnorePair_queryUsesPairUniqueConstraint() throws NoSuchMethodException {
        Method method = AgentNoteThreadRepository.class.getMethod("insertIgnorePair", Long.class, Long.class);

        Query query = method.getAnnotation(Query.class);

        assertThat(method.isAnnotationPresent(Modifying.class)).isTrue();
        assertThat(query.nativeQuery()).isTrue();
        assertThat(query.value())
                .contains("INSERT INTO agent_note_threads")
                .contains("created_at")
                .contains("modified_at")
                .contains("ON CONFLICT ON CONSTRAINT uk_agent_note_threads_pair DO NOTHING");
    }
}
