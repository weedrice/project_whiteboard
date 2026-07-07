package com.weedrice.whiteboard.domain.search.semantic;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SemanticSearchEmbeddingRepositoryTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 7, 7, 12, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            FIXED_NOW.toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC);

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final SemanticSearchEmbeddingRepository repository =
            new SemanticSearchEmbeddingRepository(jdbcTemplate, FIXED_CLOCK);

    @Test
    void tombstone_usesFixedClockForDeletedAndUpdatedAt() {
        repository.tombstone("POST", 1L);

        verify(jdbcTemplate).update(
                contains("UPDATE semantic_search_embeddings"),
                eq(FIXED_NOW),
                eq(FIXED_NOW),
                eq("POST"),
                eq(1L));
    }

    @Test
    void upsertPost_usesFixedClockForCreatedAndUpdatedAt() {
        repository.upsertPost(
                1L,
                2L,
                3L,
                null,
                "text",
                "hash",
                "model",
                new float[]{0.1f, 0.2f});

        org.mockito.ArgumentCaptor<Object[]> argsCaptor = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("INSERT INTO semantic_search_embeddings"), argsCaptor.capture());
        Object[] args = argsCaptor.getValue();
        assertThat(args[9]).isEqualTo("[0.10000000,0.20000000]");
        assertThat(args[10]).isEqualTo(FIXED_NOW);
        assertThat(args[11]).isEqualTo(FIXED_NOW);
    }
}
