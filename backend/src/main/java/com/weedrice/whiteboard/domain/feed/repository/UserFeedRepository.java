package com.weedrice.whiteboard.domain.feed.repository;

import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface UserFeedRepository extends JpaRepository<UserFeed, Long> {
    Page<UserFeed> findByTargetUserOrderByCreatedAtDesc(User targetUser, Pageable pageable);

    @Query("""
            SELECT uf.targetUser.userId
            FROM UserFeed uf
            WHERE uf.targetUser.userId IN :targetUserIds
              AND uf.feedType = :feedType
              AND uf.contentType = :contentType
              AND uf.contentId = :contentId
              AND uf.sourceCriteria = :sourceCriteria
              AND uf.criteriaId = :criteriaId
            """)
    List<Long> findExistingTargetUserIds(
            @Param("targetUserIds") Collection<Long> targetUserIds,
            @Param("feedType") String feedType,
            @Param("contentType") String contentType,
            @Param("contentId") Long contentId,
            @Param("sourceCriteria") String sourceCriteria,
            @Param("criteriaId") Long criteriaId);

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
            VALUES (
                :targetUserId,
                :feedType,
                :contentType,
                :contentId,
                :sourceCriteria,
                :criteriaId,
                'N',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT ON CONSTRAINT uk_user_feeds_target_content_source DO NOTHING
            """, nativeQuery = true)
    int insertIgnore(
            @Param("targetUserId") Long targetUserId,
            @Param("feedType") String feedType,
            @Param("contentType") String contentType,
            @Param("contentId") Long contentId,
            @Param("sourceCriteria") String sourceCriteria,
            @Param("criteriaId") Long criteriaId);
}
