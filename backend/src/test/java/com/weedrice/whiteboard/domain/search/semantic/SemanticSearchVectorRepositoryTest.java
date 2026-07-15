package com.weedrice.whiteboard.domain.search.semantic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

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
        when(jdbcTemplate.query(
                anyString(),
                any(MapSqlParameterSource.class),
                org.mockito.ArgumentMatchers.<RowMapper<SemanticSearchRow>>any()))
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
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                paramsCaptor.capture(),
                org.mockito.ArgumentMatchers.<RowMapper<SemanticSearchRow>>any());

        assertThat(sqlCaptor.getValue())
                .contains("1 - (e.embedding <=> CAST(:queryEmbedding AS vector)) AS similarity")
                .contains("LEFT JOIN agents a")
                .contains("author_display_name")
                .contains("ORDER BY similarity DESC, created_at DESC, content_id DESC")
                .contains("LIMIT :limit OFFSET :offset");
        assertThat(paramsCaptor.getValue().getValue("queryEmbedding")).isEqualTo("[0.1,0.2]");
        assertThat(paramsCaptor.getValue().getValue("blockedUserIds")).isEqualTo(List.of(9L));
        assertThat(paramsCaptor.getValue().getValue("limit")).isEqualTo(20);
        assertThat(paramsCaptor.getValue().getValue("offset")).isEqualTo(40L);
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
                .contains("b.is_active = 'Y' AND b.is_public = 'Y'")
                .contains("p.is_secret = 'N'")
                .contains("p.user_id NOT IN (:blockedUserIds)")
                .contains("u.user_id NOT IN (:blockedUserIds)")
                .contains("post_author.status = 'ACTIVE'")
                .contains("UPPER(s.type) = 'BAN'")
                .doesNotContain(":viewerSuperAdmin = TRUE")
                .doesNotContain("adm.is_active = 'Y'")
                .doesNotContain("e.embedding <=>")
                .doesNotContain("LEFT JOIN agents")
                .doesNotContain("author_display_name")
                .doesNotContain("ORDER BY")
                .doesNotContain("LIMIT :limit")
                .doesNotContain("OFFSET :offset");
    }

    @Test
    void findRelatedPostIds_filtersByMinimumSimilarityBeforeCandidateLimit() {
        Map<String, Object> noMatchRow = new HashMap<>();
        noMatchRow.put("source_embedding_available", true);
        noMatchRow.put("post_id", null);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(noMatchRow));
        SemanticSearchQueryContext context = new SemanticSearchQueryContext("free", 7L, false, List.of(9L));

        SemanticRelatedPostResult result = repository.findRelatedPostIds(1L, context, 3, 0.55);

        assertThat(result.sourceEmbeddingAvailable()).isTrue();
        assertThat(result.postIds()).isEmpty();
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), paramsCaptor.capture());
        assertThat(sqlCaptor.getValue())
                .contains("1 - (e.embedding <=> source.embedding) >= :minSimilarity")
                .contains("ORDER BY e.embedding <=> source.embedding ASC")
                .contains("LEFT JOIN candidates ON TRUE");
        assertThat(sqlCaptor.getValue().indexOf(">= :minSimilarity"))
                .isLessThan(sqlCaptor.getValue().lastIndexOf("LIMIT :limit"));
        assertThat(paramsCaptor.getValue().getValue("minSimilarity")).isEqualTo(0.55);
        assertThat(paramsCaptor.getValue().getValue("limit")).isEqualTo(3);
    }

    @Test
    void findRelatedPostIds_distinguishesMissingSourceEmbeddingFromNoMatches() {
        Map<String, Object> missingSourceRow = new HashMap<>();
        missingSourceRow.put("source_embedding_available", false);
        missingSourceRow.put("post_id", null);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(missingSourceRow));

        SemanticRelatedPostResult result = repository.findRelatedPostIds(
                1L,
                new SemanticSearchQueryContext("free", null, false, List.of()),
                3,
                0.55);

        assertThat(result.sourceEmbeddingAvailable()).isFalse();
        assertThat(result.postIds()).isEmpty();
    }
}
