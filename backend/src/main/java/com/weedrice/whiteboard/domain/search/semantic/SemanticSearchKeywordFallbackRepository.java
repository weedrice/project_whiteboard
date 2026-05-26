package com.weedrice.whiteboard.domain.search.semantic;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
                p.contents AS excerpt,
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
              AND (
                    LOWER(COALESCE(p.title, '')) LIKE :keywordPattern ESCAPE '!'
                    OR LOWER(COALESCE(p.contents, '')) LIKE :keywordPattern ESCAPE '!'
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
              AND p.is_deleted = 'N'
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
              AND (
                    LOWER(COALESCE(p.title, '')) LIKE :keywordPattern ESCAPE '!'
                    OR LOWER(COALESCE(p.contents, '')) LIKE :keywordPattern ESCAPE '!'
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
              AND p.is_deleted = 'N'
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
        String sql = buildUnionSql(query) + """
                ORDER BY created_at DESC, content_type ASC, content_id DESC
                LIMIT :limit OFFSET :offset
                """;
        return jdbcTemplate.query(sql, params(query), ROW_MAPPER);
    }

    long count(SemanticSearchKeywordQuery query) {
        String sql = "SELECT COUNT(*) FROM (" + buildCountUnionSql(query) + ") semantic_keyword_count";
        Long count = jdbcTemplate.queryForObject(sql, params(query), Long.class);
        return count != null ? count : 0L;
    }

    private String buildUnionSql(SemanticSearchKeywordQuery query) {
        List<String> selects = new ArrayList<>();
        String boardPredicate = boardPredicate(query);
        String postPrivacyPredicate = postPrivacyPredicate(query);
        if (query.contentType().includesPosts()) {
            selects.add(String.format(POST_SELECT, boardPredicate, postPrivacyPredicate));
        }
        if (query.contentType().includesComments()) {
            selects.add(String.format(COMMENT_SELECT, boardPredicate, postPrivacyPredicate));
        }
        return String.join("\nUNION ALL\n", selects) + "\n";
    }

    private String buildCountUnionSql(SemanticSearchKeywordQuery query) {
        List<String> selects = new ArrayList<>();
        String boardPredicate = boardPredicate(query);
        String postPrivacyPredicate = postPrivacyPredicate(query);
        if (query.contentType().includesPosts()) {
            selects.add(String.format(POST_COUNT_SELECT, boardPredicate, postPrivacyPredicate));
        }
        if (query.contentType().includesComments()) {
            selects.add(String.format(COMMENT_COUNT_SELECT, boardPredicate, postPrivacyPredicate));
        }
        return String.join("\nUNION ALL\n", selects) + "\n";
    }

    private String boardPredicate(SemanticSearchKeywordQuery query) {
        String blockedPost = query.hasBlockedUserIds() ? " AND p.user_id NOT IN (:blockedUserIds)" : "";
        if (!query.hasBoardUrl()) {
            return "b.is_active = 'Y' AND b.is_public = 'Y'" + blockedPost;
        }
        return """
                b.board_url = :boardUrl
                AND (
                    (b.is_active = 'Y' AND b.is_public = 'Y')
                    OR (:viewerUserId IS NOT NULL AND :viewerSuperAdmin = TRUE)
                    OR (:viewerUserId IS NOT NULL AND EXISTS (
                        SELECT 1 FROM admins adm
                        WHERE adm.user_id = :viewerUserId
                          AND adm.board_id = b.board_id
                          AND adm.is_active = 'Y'
                    ))
                )
                """ + blockedPost;
    }

    private String postPrivacyPredicate(SemanticSearchKeywordQuery query) {
        String blockedComment = query.hasBlockedUserIds() ? " AND u.user_id NOT IN (:blockedUserIds)" : "";
        return """
                (
                    p.is_secret = 'N'
                    OR (:viewerUserId IS NOT NULL AND p.user_id = :viewerUserId)
                    OR (:viewerUserId IS NOT NULL AND :viewerSuperAdmin = TRUE)
                    OR (:viewerUserId IS NOT NULL AND EXISTS (
                        SELECT 1 FROM admins adm
                        WHERE adm.user_id = :viewerUserId
                          AND adm.board_id = b.board_id
                          AND adm.is_active = 'Y'
                    ))
                )
                """ + blockedComment;
    }

    private MapSqlParameterSource params(SemanticSearchKeywordQuery query) {
        List<Long> blockedUserIds = query.hasBlockedUserIds() ? query.blockedUserIds() : List.of(-1L);
        return new MapSqlParameterSource()
                .addValue("keywordPattern", keywordPattern(query.keyword()))
                .addValue("boardUrl", query.boardUrl())
                .addValue("viewerUserId", query.viewerUserId())
                .addValue("viewerSuperAdmin", query.viewerSuperAdmin())
                .addValue("blockedUserIds", blockedUserIds)
                .addValue("limit", query.limit())
                .addValue("offset", query.offset());
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

    private static final RowMapper<SemanticSearchRow> ROW_MAPPER = new RowMapper<>() {
        @Override
        public SemanticSearchRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new SemanticSearchRow(
                    rs.getString("content_type"),
                    rs.getLong("content_id"),
                    rs.getLong("post_id"),
                    rs.getLong("board_id"),
                    rs.getString("board_url"),
                    rs.getString("board_name"),
                    rs.getString("title"),
                    rs.getString("excerpt"),
                    getNullableDouble(rs, "similarity"),
                    rs.getString("rank_source"),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    getNullableLong(rs, "author_user_id"),
                    getNullableLong(rs, "author_agent_id"),
                    rs.getString("author_type"),
                    rs.getString("author_display_name"),
                    rs.getString("author_profile_image_url"));
        }
    };

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
