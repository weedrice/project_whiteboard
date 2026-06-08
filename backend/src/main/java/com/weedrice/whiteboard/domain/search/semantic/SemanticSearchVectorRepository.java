package com.weedrice.whiteboard.domain.search.semantic;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
class SemanticSearchVectorRepository {
    private static final String POST_SELECT = """
            SELECT
                'POST' AS content_type,
                p.post_id AS content_id,
                p.post_id AS post_id,
                b.board_id AS board_id,
                b.board_url AS board_url,
                b.board_name AS board_name,
                p.title AS title,
                e.embedding_text AS excerpt,
                1 - (e.embedding <=> CAST(:queryEmbedding AS vector)) AS similarity,
                'VECTOR' AS rank_source,
                p.created_at AS created_at,
                u.user_id AS author_user_id,
                a.agent_id AS author_agent_id,
                CASE WHEN a.agent_id IS NULL THEN 'USER' ELSE 'AGENT' END AS author_type,
                COALESCE(a.name, u.display_name) AS author_display_name,
                u.profile_image_url AS author_profile_image_url
            FROM semantic_search_embeddings e
            JOIN posts p ON p.post_id = e.content_id
            JOIN boards b ON b.board_id = p.board_id
            JOIN users u ON u.user_id = p.user_id
            LEFT JOIN agents a ON a.agent_id = p.agent_id
            WHERE e.content_type = 'POST'
              AND e.deleted_at IS NULL
              AND p.is_deleted = 'N'
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
                e.embedding_text AS excerpt,
                1 - (e.embedding <=> CAST(:queryEmbedding AS vector)) AS similarity,
                'VECTOR' AS rank_source,
                c.created_at AS created_at,
                u.user_id AS author_user_id,
                a.agent_id AS author_agent_id,
                CASE WHEN a.agent_id IS NULL THEN 'USER' ELSE 'AGENT' END AS author_type,
                COALESCE(a.name, u.display_name) AS author_display_name,
                u.profile_image_url AS author_profile_image_url
            FROM semantic_search_embeddings e
            JOIN comments c ON c.comment_id = e.content_id
            JOIN posts p ON p.post_id = c.post_id
            JOIN boards b ON b.board_id = p.board_id
            JOIN users u ON u.user_id = c.user_id
            JOIN users post_author ON post_author.user_id = p.user_id
            LEFT JOIN agents a ON a.agent_id = c.agent_id
            WHERE e.content_type = 'COMMENT'
              AND e.deleted_at IS NULL
              AND c.is_deleted = 'N'
              AND p.is_deleted = 'N'
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
            SELECT e.content_id
            FROM semantic_search_embeddings e
            JOIN posts p ON p.post_id = e.content_id
            JOIN boards b ON b.board_id = p.board_id
            JOIN users u ON u.user_id = p.user_id
            WHERE e.content_type = 'POST'
              AND e.deleted_at IS NULL
              AND p.is_deleted = 'N'
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
            SELECT e.content_id
            FROM semantic_search_embeddings e
            JOIN comments c ON c.comment_id = e.content_id
            JOIN posts p ON p.post_id = c.post_id
            JOIN boards b ON b.board_id = p.board_id
            JOIN users u ON u.user_id = c.user_id
            JOIN users post_author ON post_author.user_id = p.user_id
            WHERE e.content_type = 'COMMENT'
              AND e.deleted_at IS NULL
              AND c.is_deleted = 'N'
              AND p.is_deleted = 'N'
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

    List<SemanticSearchRow> search(SemanticSearchQuery query) {
        return jdbcTemplate.query(searchSql(query), params(query), SemanticSearchRowMapper.INSTANCE);
    }

    long count(SemanticSearchQuery query) {
        Long count = jdbcTemplate.queryForObject(countSql(query), params(query), Long.class);
        return count != null ? count : 0L;
    }

    String searchSql(SemanticSearchQuery query) {
        return SemanticSearchSqlFragments.buildUnionSql(query, POST_SELECT, COMMENT_SELECT) + """
                ORDER BY similarity DESC, created_at DESC, content_id DESC
                LIMIT :limit OFFSET :offset
                """;
    }

    String countSql(SemanticSearchQuery query) {
        return "SELECT COUNT(*) FROM ("
                + SemanticSearchSqlFragments.buildUnionSql(query, POST_COUNT_SELECT, COMMENT_COUNT_SELECT)
                + ") semantic_count";
    }

    MapSqlParameterSource params(SemanticSearchQuery query) {
        return SemanticSearchSqlFragments.commonParams(query)
                .addValue("queryEmbedding", query.embeddingVector());
    }

}
