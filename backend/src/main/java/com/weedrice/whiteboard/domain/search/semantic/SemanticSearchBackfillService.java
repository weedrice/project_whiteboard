package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SemanticSearchBackfillService {

    private final JdbcTemplate jdbcTemplate;
    private final SemanticSearchJobRepository jobRepository;

    @Transactional
    public int enqueueBackfill(String contentType, int limit) {
        SemanticSearchContentType normalizedContentType = SemanticSearchContentType.from(contentType);
        if (limit < 1 || limit > 1000) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        int count = 0;
        if (normalizedContentType.includesPosts()) {
            count += enqueuePosts(limit);
        }
        if (normalizedContentType.includesComments()) {
            count += enqueueComments(limit);
        }
        return count;
    }

    private int enqueuePosts(int limit) {
        List<Long> postIds = jdbcTemplate.queryForList("""
                SELECT p.post_id
                FROM posts p
                LEFT JOIN semantic_search_embeddings e
                       ON e.content_type = 'POST'
                      AND e.content_id = p.post_id
                WHERE p.is_deleted = 'N'
                  AND (e.embedding_id IS NULL OR e.deleted_at IS NOT NULL)
                  AND NOT EXISTS (
                        SELECT 1
                        FROM semantic_search_jobs j
                        WHERE j.content_type = 'POST'
                          AND j.content_id = p.post_id
                          AND j.status IN ('PENDING', 'PROCESSING')
                  )
                ORDER BY p.created_at ASC, p.post_id ASC
                LIMIT ?
                """, Long.class, limit);
        jobRepository.enqueueAll("POST", postIds, SemanticSearchIndexAction.UPSERT);
        return postIds.size();
    }

    private int enqueueComments(int limit) {
        List<Long> commentIds = jdbcTemplate.queryForList("""
                SELECT c.comment_id
                FROM comments c
                JOIN posts p ON p.post_id = c.post_id
                LEFT JOIN semantic_search_embeddings e
                       ON e.content_type = 'COMMENT'
                      AND e.content_id = c.comment_id
                WHERE c.is_deleted = 'N'
                  AND p.is_deleted = 'N'
                  AND (e.embedding_id IS NULL OR e.deleted_at IS NOT NULL)
                  AND NOT EXISTS (
                        SELECT 1
                        FROM semantic_search_jobs j
                        WHERE j.content_type = 'COMMENT'
                          AND j.content_id = c.comment_id
                          AND j.status IN ('PENDING', 'PROCESSING')
                  )
                ORDER BY c.created_at ASC, c.comment_id ASC
                LIMIT ?
                """, Long.class, limit);
        jobRepository.enqueueAll("COMMENT", commentIds, SemanticSearchIndexAction.UPSERT);
        return commentIds.size();
    }
}
