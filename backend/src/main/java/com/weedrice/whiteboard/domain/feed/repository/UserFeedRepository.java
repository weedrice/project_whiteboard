package com.weedrice.whiteboard.domain.feed.repository;

import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserFeedRepository extends JpaRepository<UserFeed, Long>, UserFeedRepositoryCustom {
    @Modifying
    @Query(value = """
            INSERT INTO user_feeds (
                target_user_id,
                feed_type,
                content_type,
                content_id,
                source_criteria,
                criteria_id,
                is_read,
                created_at,
                modified_at
            )
            SELECT DISTINCT
                bs.user_id,
                :feedType,
                :contentType,
                :contentId,
                :sourceCriteria,
                :criteriaId,
                'N',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            FROM board_subscriptions bs
            JOIN users u ON u.user_id = bs.user_id
            WHERE bs.board_id = :boardId
              AND bs.role <> 'BANNED'
              AND u.status = 'ACTIVE'
              AND u.deleted_at IS NULL
            ON CONFLICT ON CONSTRAINT uk_user_feeds_target_content_source DO NOTHING
            """, nativeQuery = true)
    int insertSubscriptionPostFeeds(
            @Param("boardId") Long boardId,
            @Param("feedType") String feedType,
            @Param("contentType") String contentType,
            @Param("contentId") Long contentId,
            @Param("sourceCriteria") String sourceCriteria,
            @Param("criteriaId") Long criteriaId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            DELETE FROM user_feeds
            WHERE content_type = :contentType
              AND NOT EXISTS (
                    SELECT 1
                    FROM posts p
                    WHERE p.post_id = user_feeds.content_id
                      AND p.is_deleted = 'N'
              )
            """, nativeQuery = true)
    int deleteHardStalePostFeeds(@Param("contentType") String contentType);
}
