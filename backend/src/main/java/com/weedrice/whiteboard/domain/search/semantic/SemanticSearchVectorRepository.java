package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.domain.actor.query.ContentAuthorVisibility;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

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
                p.contents AS excerpt,
                1 - (e.embedding <=> CAST(:queryEmbedding AS vector)) AS similarity,
                'VECTOR' AS rank_source,
                p.created_at AS created_at,
                u.user_id AS author_user_id,
                a.agent_id AS author_agent_id,
                CASE WHEN a.agent_id IS NULL THEN 'USER' ELSE 'AGENT' END AS author_type,
                COALESCE(a.name, u.display_name) AS author_display_name,
                u.profile_image_url AS author_profile_image_url,
                (u.status = 'DELETED' OR u.deleted_at IS NOT NULL) AS author_withdrawn
            FROM semantic_search_embeddings e
            JOIN posts p ON p.post_id = e.content_id
            JOIN boards b ON b.board_id = p.board_id
            JOIN users u ON u.user_id = p.user_id
            LEFT JOIN agents a ON a.agent_id = p.agent_id
            WHERE e.content_type = 'POST'
              AND e.deleted_at IS NULL
              AND p.is_deleted = 'N'
              AND p.is_blinded = 'N'
              AND %s
              AND %s
            """ + ContentAuthorVisibility.sql("u");

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
                1 - (e.embedding <=> CAST(:queryEmbedding AS vector)) AS similarity,
                'VECTOR' AS rank_source,
                c.created_at AS created_at,
                u.user_id AS author_user_id,
                a.agent_id AS author_agent_id,
                CASE WHEN a.agent_id IS NULL THEN 'USER' ELSE 'AGENT' END AS author_type,
                COALESCE(a.name, u.display_name) AS author_display_name,
                u.profile_image_url AS author_profile_image_url,
                (u.status = 'DELETED' OR u.deleted_at IS NOT NULL) AS author_withdrawn
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
              AND c.is_blinded = 'N'
              AND p.is_deleted = 'N'
              AND p.is_blinded = 'N'
              AND %s
              AND %s
            """ + ContentAuthorVisibility.sql("u", "post_author");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /** Called within the query transaction; a pooled connection's session settings remain unchanged. */
    void configureCandidateSearch(int candidateLimit) {
        jdbcTemplate.queryForObject("""
                SELECT set_config('hnsw.ef_search',
                    GREATEST(:candidateLimit, 40, NULLIF(current_setting('hnsw.ef_search', true), '')::integer)::text,
                    true)
                """, new MapSqlParameterSource("candidateLimit", candidateLimit), String.class);
    }

    List<SemanticSearchRow> search(SemanticSearchQuery query) {
        return jdbcTemplate.query(searchSql(query), params(query), SemanticSearchRowMapper.INSTANCE);
    }

    long count(SemanticSearchQuery query) {
        Long count = jdbcTemplate.queryForObject(countSql(query), params(query), Long.class);
        return count != null ? count : 0L;
    }

    String searchSql(SemanticSearchQuery query) {
        return "SELECT * FROM (" + candidateSql(query) + """
                ) ranked_candidates
                ORDER BY similarity DESC, created_at DESC, content_type ASC, content_id DESC
                LIMIT :limit OFFSET :offset
                """;
    }

    String countSql(SemanticSearchQuery query) {
        return "SELECT COUNT(*) FROM (" + candidateSql(query) + ") semantic_count";
    }

    private String candidateSql(SemanticSearchQuery query) {
        java.util.List<String> branches = new java.util.ArrayList<>();
        if (query.contentType().includesPosts()) {
            branches.add(nearestBranch(query, POST_SELECT));
        }
        if (query.contentType().includesComments()) {
            branches.add(nearestBranch(query, COMMENT_SELECT));
        }
        return "SELECT * FROM (" + String.join(" UNION ALL ", branches) + """
                ) nearest_candidates
                WHERE similarity >= :minSimilarity
                ORDER BY similarity DESC, created_at DESC, content_type ASC, content_id DESC
                LIMIT :candidateLimit
                """;
    }

    private String nearestBranch(SemanticSearchQuery query, String select) {
        String eligibleSelect = select.formatted(SemanticSearchSqlFragments.boardPredicate(query),
                SemanticSearchSqlFragments.postPrivacyPredicate(query));
        // HNSW applies eligibility filters after exploring neighbors. Reuse a full ANN pool,
        // but replace an underfilled pool with an exact scan using the identical predicates.
        // The scalar CTE count is a one-time guard: a full pool never executes the exact scan.
        // Adding zero to distance prevents the fallback from selecting HNSW again, including
        // on pgvector versions without iterative scans. Preserve boundary ties in both paths.
        return "(WITH ann_candidates AS MATERIALIZED (" + eligibleSelect + """
                ORDER BY e.embedding <=> CAST(:queryEmbedding AS vector) ASC
                FETCH FIRST :candidateLimit ROWS WITH TIES
                )
                SELECT * FROM ann_candidates
                WHERE (SELECT COUNT(*) FROM ann_candidates) >= :candidateLimit
                UNION ALL
                SELECT * FROM (
                """ + eligibleSelect + """
                AND (SELECT COUNT(*) FROM ann_candidates) < :candidateLimit
                ORDER BY (e.embedding <=> CAST(:queryEmbedding AS vector)) + 0 ASC
                FETCH FIRST :candidateLimit ROWS WITH TIES
                ) exact_candidates
                )
                """;
    }

    MapSqlParameterSource params(SemanticSearchQuery query) {
        return SemanticSearchSqlFragments.commonParams(query)
                .addValue("queryEmbedding", query.embeddingVector())
                .addValue("minSimilarity", query.minSimilarity())
                .addValue("candidateLimit", query.candidateLimit());
    }

    SemanticRelatedPostResult findRelatedPostIds(
            Long sourcePostId,
            SemanticSearchQueryContext context,
            int size,
            double minSimilarity) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                relatedPostSql(context),
                relatedPostParams(sourcePostId, context, size, minSimilarity));
        if (rows.isEmpty()) {
            return SemanticRelatedPostResult.sourceEmbeddingUnavailable();
        }

        boolean sourceEmbeddingAvailable = Boolean.TRUE.equals(rows.get(0).get("source_embedding_available"));
        List<Long> postIds = rows.stream()
                .map(row -> row.get("post_id"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::longValue)
                .toList();
        return new SemanticRelatedPostResult(sourceEmbeddingAvailable, postIds);
    }

    private String relatedPostSql(SemanticSearchQueryContext context) {
        String blockedPredicate = context.blockedUserIds() == null || context.blockedUserIds().isEmpty()
                ? ""
                : " AND p.user_id NOT IN (:blockedUserIds)";
        String boardPredicate = context.boardUrl() == null || context.boardUrl().isBlank()
                ? " AND (b.is_listed = 'Y' OR b.is_listed IS NULL)"
                : " AND b.board_url = :boardUrl";
        return """
                WITH source_embedding AS (
                    SELECT e.embedding
                    FROM semantic_search_embeddings e
                    JOIN posts source_post ON source_post.post_id = e.content_id
                    JOIN boards b ON b.board_id = source_post.board_id
                    WHERE e.content_type = 'POST'
                      AND e.content_id = :sourcePostId
                      AND e.deleted_at IS NULL
                      AND source_post.is_deleted = 'N'
                      AND source_post.is_blinded = 'N'
                      AND source_post.is_secret = 'N'
                      AND b.is_active = 'Y'
                      AND b.is_public = 'Y'
                      %s
                    LIMIT 1
                )
                , candidates AS (
                    SELECT
                        p.post_id,
                        1 - (e.embedding <=> source.embedding) AS similarity,
                        p.created_at
                    FROM semantic_search_embeddings e
                    JOIN source_embedding source ON TRUE
                    JOIN posts p ON p.post_id = e.content_id
                    JOIN boards b ON b.board_id = p.board_id
                    JOIN users u ON u.user_id = p.user_id
                    WHERE e.content_type = 'POST'
                      AND e.deleted_at IS NULL
                      AND p.post_id <> :sourcePostId
                      AND p.is_deleted = 'N'
                      AND p.is_blinded = 'N'
                      AND p.is_secret = 'N'
                      AND b.is_active = 'Y'
                      AND b.is_public = 'Y'
                      %s
                      AND 1 - (e.embedding <=> source.embedding) >= :minSimilarity
                      %s
                      %s
                    ORDER BY e.embedding <=> source.embedding ASC, p.created_at DESC, p.post_id DESC
                    LIMIT :limit
                )
                SELECT
                    EXISTS (SELECT 1 FROM source_embedding) AS source_embedding_available,
                    candidates.post_id
                FROM (SELECT TRUE AS marker) marker
                LEFT JOIN candidates ON TRUE
                ORDER BY candidates.similarity DESC NULLS LAST,
                         candidates.created_at DESC NULLS LAST,
                         candidates.post_id DESC NULLS LAST
                """.formatted(boardPredicate, ContentAuthorVisibility.sql("u"), boardPredicate, blockedPredicate);
    }

    private MapSqlParameterSource relatedPostParams(
            Long sourcePostId,
            SemanticSearchQueryContext context,
            int size,
            double minSimilarity) {
        return new MapSqlParameterSource()
                .addValue("sourcePostId", sourcePostId)
                .addValue("boardUrl", context.boardUrl())
                .addValue("blockedUserIds", context.blockedUserIds() == null || context.blockedUserIds().isEmpty()
                        ? List.of(-1L)
                        : context.blockedUserIds())
                .addValue("minSimilarity", minSimilarity)
                .addValue("limit", size);
    }
}
