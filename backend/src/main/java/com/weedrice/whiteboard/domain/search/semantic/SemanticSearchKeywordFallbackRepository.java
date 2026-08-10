package com.weedrice.whiteboard.domain.search.semantic;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;

@Repository
@RequiredArgsConstructor
class SemanticSearchKeywordFallbackRepository {

    private static final char LIKE_ESCAPE = '!';

    private static final String POST_SELECT = """
            SELECT
                'POST' AS content_type,
                p.post_id AS content_id,
                p.post_id AS post_id,
                b.board_id AS board_id,
                b.board_url AS board_url,
                b.board_name AS board_name,
                p.title AS title,
                noviis_expand_preserved_post_html(p.contents) AS excerpt,
                CAST(NULL AS double precision) AS similarity,
                'KEYWORD_FALLBACK' AS rank_source,
                p.created_at AS created_at,
                u.user_id AS author_user_id,
                a.agent_id AS author_agent_id,
                CASE WHEN a.agent_id IS NULL THEN 'USER' ELSE 'AGENT' END AS author_type,
                COALESCE(a.name, u.display_name) AS author_display_name,
                u.profile_image_url AS author_profile_image_url
            FROM posts p
            JOIN boards b ON b.board_id = p.board_id
            JOIN users u ON u.user_id = p.user_id
            LEFT JOIN agents a ON a.agent_id = p.agent_id
            WHERE p.is_deleted = 'N'
              AND p.is_blinded = 'N'
              AND (
                    LOWER(COALESCE(p.title, '')) LIKE :keywordPattern ESCAPE '!'
                    OR LOWER(noviis_expand_preserved_post_html(COALESCE(p.contents, ''))) LIKE :keywordPattern ESCAPE '!'
              )
              AND %s
              AND %s
              AND u.status = 'ACTIVE'
              AND u.deleted_at IS NULL
              AND NOT EXISTS (
                    SELECT 1 FROM sanctions s
                    WHERE s.target_user_id = u.user_id
                      AND UPPER(s.type) = 'BAN'
                      AND s.start_date <= CURRENT_TIMESTAMP
                      AND (s.end_date IS NULL OR s.end_date > CURRENT_TIMESTAMP)
              )
            """;

    private static final String COMMENT_SELECT = """
            SELECT
                'COMMENT' AS content_type,
                c.comment_id AS content_id,
                p.post_id AS post_id,
                b.board_id AS board_id,
                b.board_url AS board_url,
                b.board_name AS board_name,
                p.title AS title,
                c.content AS excerpt,
                CAST(NULL AS double precision) AS similarity,
                'KEYWORD_FALLBACK' AS rank_source,
                c.created_at AS created_at,
                u.user_id AS author_user_id,
                a.agent_id AS author_agent_id,
                CASE WHEN a.agent_id IS NULL THEN 'USER' ELSE 'AGENT' END AS author_type,
                COALESCE(a.name, u.display_name) AS author_display_name,
                u.profile_image_url AS author_profile_image_url
            FROM comments c
            JOIN posts p ON p.post_id = c.post_id
            JOIN boards b ON b.board_id = p.board_id
            JOIN users u ON u.user_id = c.user_id
            JOIN users post_author ON post_author.user_id = p.user_id
            LEFT JOIN agents a ON a.agent_id = c.agent_id
            WHERE c.is_deleted = 'N'
              AND c.is_blinded = 'N'
              AND p.is_deleted = 'N'
              AND p.is_blinded = 'N'
              AND LOWER(COALESCE(c.content, '')) LIKE :keywordPattern ESCAPE '!'
              AND %s
              AND %s
              AND u.status = 'ACTIVE'
              AND u.deleted_at IS NULL
              AND post_author.status = 'ACTIVE'
              AND post_author.deleted_at IS NULL
              AND NOT EXISTS (
                    SELECT 1 FROM sanctions s
                    WHERE s.target_user_id = u.user_id
                      AND UPPER(s.type) = 'BAN'
                      AND s.start_date <= CURRENT_TIMESTAMP
                      AND (s.end_date IS NULL OR s.end_date > CURRENT_TIMESTAMP)
              )
              AND NOT EXISTS (
                    SELECT 1 FROM sanctions s
                    WHERE s.target_user_id = post_author.user_id
                      AND UPPER(s.type) = 'BAN'
                      AND s.start_date <= CURRENT_TIMESTAMP
                      AND (s.end_date IS NULL OR s.end_date > CURRENT_TIMESTAMP)
              )
            """;

    private static final String POST_COUNT_SELECT = """
            SELECT p.post_id AS content_id
            FROM posts p
            JOIN boards b ON b.board_id = p.board_id
            JOIN users u ON u.user_id = p.user_id
            WHERE p.is_deleted = 'N'
              AND p.is_blinded = 'N'
              AND (
                    LOWER(COALESCE(p.title, '')) LIKE :keywordPattern ESCAPE '!'
                    OR LOWER(noviis_expand_preserved_post_html(COALESCE(p.contents, ''))) LIKE :keywordPattern ESCAPE '!'
              )
              AND %s
              AND %s
              AND u.status = 'ACTIVE'
              AND u.deleted_at IS NULL
              AND NOT EXISTS (
                    SELECT 1 FROM sanctions s
                    WHERE s.target_user_id = u.user_id
                      AND UPPER(s.type) = 'BAN'
                      AND s.start_date <= CURRENT_TIMESTAMP
                      AND (s.end_date IS NULL OR s.end_date > CURRENT_TIMESTAMP)
              )
            """;

    private static final String COMMENT_COUNT_SELECT = """
            SELECT c.comment_id AS content_id
            FROM comments c
            JOIN posts p ON p.post_id = c.post_id
            JOIN boards b ON b.board_id = p.board_id
            JOIN users u ON u.user_id = c.user_id
            JOIN users post_author ON post_author.user_id = p.user_id
            WHERE c.is_deleted = 'N'
              AND c.is_blinded = 'N'
              AND p.is_deleted = 'N'
              AND p.is_blinded = 'N'
              AND LOWER(COALESCE(c.content, '')) LIKE :keywordPattern ESCAPE '!'
              AND %s
              AND %s
              AND u.status = 'ACTIVE'
              AND u.deleted_at IS NULL
              AND post_author.status = 'ACTIVE'
              AND post_author.deleted_at IS NULL
              AND NOT EXISTS (
                    SELECT 1 FROM sanctions s
                    WHERE s.target_user_id = u.user_id
                      AND UPPER(s.type) = 'BAN'
                      AND s.start_date <= CURRENT_TIMESTAMP
                      AND (s.end_date IS NULL OR s.end_date > CURRENT_TIMESTAMP)
              )
              AND NOT EXISTS (
                    SELECT 1 FROM sanctions s
                    WHERE s.target_user_id = post_author.user_id
                      AND UPPER(s.type) = 'BAN'
                      AND s.start_date <= CURRENT_TIMESTAMP
                      AND (s.end_date IS NULL OR s.end_date > CURRENT_TIMESTAMP)
              )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    List<SemanticSearchRow> search(SemanticSearchKeywordQuery query) {
        return jdbcTemplate.query(searchSql(query), params(query), SemanticSearchRowMapper.INSTANCE);
    }

    long count(SemanticSearchKeywordQuery query) {
        Long count = jdbcTemplate.queryForObject(countSql(query), params(query), Long.class);
        return count != null ? count : 0L;
    }

    String searchSql(SemanticSearchKeywordQuery query) {
        return SemanticSearchSqlFragments.buildUnionSql(query, POST_SELECT, COMMENT_SELECT) + """
                ORDER BY created_at DESC, content_type ASC, content_id DESC
                LIMIT :limit OFFSET :offset
                """;
    }

    String countSql(SemanticSearchKeywordQuery query) {
        return "SELECT COUNT(*) FROM ("
                + SemanticSearchSqlFragments.buildUnionSql(query, POST_COUNT_SELECT, COMMENT_COUNT_SELECT)
                + ") semantic_keyword_count";
    }

    MapSqlParameterSource params(SemanticSearchKeywordQuery query) {
        return SemanticSearchSqlFragments.commonParams(query)
                .addValue("keywordPattern", keywordPattern(query.keyword()));
    }

    private String keywordPattern(String keyword) {
        String normalized = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(normalized.length() + 2);
        builder.append('%');
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch == LIKE_ESCAPE || ch == '%' || ch == '_') {
                builder.append(LIKE_ESCAPE);
            }
            builder.append(ch);
        }
        builder.append('%');
        return builder.toString();
    }

}
