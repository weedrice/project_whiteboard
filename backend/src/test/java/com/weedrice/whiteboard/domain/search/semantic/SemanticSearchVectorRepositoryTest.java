package com.weedrice.whiteboard.domain.search.semantic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SemanticSearchVectorRepositoryTest {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private SemanticSearchVectorRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        repository = new SemanticSearchVectorRepository(jdbcTemplate);
    }

    @Test
    void search_buildsUnionSqlWithSimilarityAndResponseColumns() {
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        SemanticSearchQuery query = new SemanticSearchQuery(
                SemanticSearchContentType.ALL,
                null,
                7L,
                false,
                List.of(9L),
                "[0.1,0.2]",
                20,
                40);

        repository.search(query);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));

        assertThat(sqlCaptor.getValue())
                .contains("1 - (e.embedding <=> CAST(:queryEmbedding AS vector)) AS similarity")
                .contains("LEFT JOIN agents a")
                .contains("author_display_name")
                .contains("ORDER BY similarity DESC, created_at DESC, content_id DESC")
                .contains("LIMIT :limit OFFSET :offset");
    }

    @Test
    void count_usesDedicatedCountSqlWithoutResponseColumns() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(5L);
        SemanticSearchQuery query = new SemanticSearchQuery(
                SemanticSearchContentType.ALL,
                "private",
                7L,
                true,
                List.of(9L),
                "[0.1,0.2]",
                10,
                0);

        long count = repository.count(query);

        assertThat(count).isEqualTo(5L);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), any(MapSqlParameterSource.class), eq(Long.class));
        assertThat(sqlCaptor.getValue())
                .contains("SELECT COUNT(*) FROM")
                .contains("UNION ALL")
                .contains("b.board_url = :boardUrl")
                .contains(":viewerSuperAdmin = TRUE")
                .contains("p.user_id NOT IN (:blockedUserIds)")
                .contains("u.user_id NOT IN (:blockedUserIds)")
                .contains("post_author.status = 'ACTIVE'")
                .contains("UPPER(s.type) = 'BAN'")
                .doesNotContain("e.embedding <=>")
                .doesNotContain("LEFT JOIN agents")
                .doesNotContain("author_display_name")
                .doesNotContain("ORDER BY")
                .doesNotContain("LIMIT :limit")
                .doesNotContain("OFFSET :offset");
    }
}
