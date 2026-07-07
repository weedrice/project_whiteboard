package com.weedrice.whiteboard.domain.search.semantic;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
class SemanticSearchEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    void upsertPost(Long postId, Long boardId, Long authorUserId, Long authorAgentId,
            String embeddingText, String embeddingHash, String embeddingModel, float[] embedding) {
        upsert("POST", postId, postId, boardId, authorUserId, authorAgentId,
                embeddingText, embeddingHash, embeddingModel, embedding);
    }

    void upsertComment(Long commentId, Long postId, Long boardId, Long authorUserId, Long authorAgentId,
            String embeddingText, String embeddingHash, String embeddingModel, float[] embedding) {
        upsert("COMMENT", commentId, postId, boardId, authorUserId, authorAgentId,
                embeddingText, embeddingHash, embeddingModel, embedding);
    }

    void tombstone(String contentType, Long contentId) {
        LocalDateTime now = now();
        jdbcTemplate.update("""
                UPDATE semantic_search_embeddings
                SET deleted_at = ?, updated_at = ?
                WHERE content_type = ? AND content_id = ?
                """, now, now, contentType, contentId);
    }

    void tombstoneCommentsByPostId(Long postId) {
        LocalDateTime now = now();
        jdbcTemplate.update("""
                UPDATE semantic_search_embeddings
                SET deleted_at = ?, updated_at = ?
                WHERE content_type = 'COMMENT' AND post_id = ?
                """, now, now, postId);
    }

    private void upsert(String contentType, Long contentId, Long postId, Long boardId, Long authorUserId,
            Long authorAgentId, String embeddingText, String embeddingHash, String embeddingModel, float[] embedding) {
        LocalDateTime now = now();
        jdbcTemplate.update("""
                INSERT INTO semantic_search_embeddings (
                    content_type, content_id, post_id, board_id, author_user_id, author_agent_id,
                    embedding_text, embedding_hash, embedding_model, embedding, created_at, updated_at, deleted_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector), ?, ?, NULL)
                ON CONFLICT (content_type, content_id)
                DO UPDATE SET
                    post_id = EXCLUDED.post_id,
                    board_id = EXCLUDED.board_id,
                    author_user_id = EXCLUDED.author_user_id,
                    author_agent_id = EXCLUDED.author_agent_id,
                    embedding_text = EXCLUDED.embedding_text,
                    embedding_hash = EXCLUDED.embedding_hash,
                    embedding_model = EXCLUDED.embedding_model,
                    embedding = EXCLUDED.embedding,
                    updated_at = EXCLUDED.updated_at,
                    deleted_at = NULL
                """,
                contentType,
                contentId,
                postId,
                boardId,
                authorUserId,
                authorAgentId,
                embeddingText,
                embeddingHash,
                embeddingModel,
                SemanticSearchVectorFormatter.format(embedding),
                now,
                now);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
