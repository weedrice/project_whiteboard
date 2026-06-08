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

class SemanticSearchKeywordFallbackRepositoryTest {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private SemanticSearchKeywordFallbackRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        repository = new SemanticSearchKeywordFallbackRepository(jdbcTemplate);
    }

    @Test
    void search_buildsUnionSqlWithSafetyFiltersAndEscapedKeywordPattern() {
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        SemanticSearchKeywordQuery query = new SemanticSearchKeywordQuery(
                SemanticSearchContentType.ALL,
                "100%_match!",
                null,
                7L,
                false,
                List.of(9L),
                20,
                40);

        repository.search(query);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));

        String sql = sqlCaptor.getValue();
        assertThat(sql)
                .contains("UNION ALL")
                .contains("b.is_active = 'Y' AND b.is_public = 'Y'")
                .contains("u.status = 'ACTIVE'")
                .contains("post_author.status = 'ACTIVE'")
                .contains("UPPER(s.type) = 'BAN'")
                .contains("p.user_id NOT IN (:blockedUserIds)")
                .contains("u.user_id NOT IN (:blockedUserIds)")
                .contains("ORDER BY created_at DESC, content_type ASC, content_id DESC");
        assertThat(paramsCaptor.getValue().getValue("keywordPattern")).isEqualTo("%100!%!_match!!%");
    }

    @Test
    void count_usesDedicatedCountSqlWithoutResponseColumns() {
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(3L);
        SemanticSearchKeywordQuery query = new SemanticSearchKeywordQuery(
                SemanticSearchContentType.ALL,
                "hello",
                "private",
                7L,
                true,
                List.of(),
                10,
                0);

        long count = repository.count(query);

        assertThat(count).isEqualTo(3L);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), eq(Long.class));
        assertThat(sqlCaptor.getValue())
                .contains("SELECT COUNT(*) FROM")
                .contains("UNION ALL")
                .contains("b.board_url = :boardUrl")
                .contains(":viewerSuperAdmin = TRUE")
                .contains("LOWER(COALESCE(p.title, '')) LIKE :keywordPattern ESCAPE '!'")
                .contains("LOWER(COALESCE(c.content, '')) LIKE :keywordPattern ESCAPE '!'")
                .contains("post_author.status = 'ACTIVE'")
                .contains("UPPER(s.type) = 'BAN'")
                .doesNotContain("LEFT JOIN agents")
                .doesNotContain("author_display_name")
                .doesNotContain("ORDER BY")
                .doesNotContain("LIMIT :limit")
                .doesNotContain("OFFSET :offset");
        assertThat(paramsCaptor.getValue().getValue("blockedUserIds")).isEqualTo(List.of(-1L));
        assertThat(paramsCaptor.getValue().getValue("keywordPattern")).isEqualTo("%hello%");
    }
}
