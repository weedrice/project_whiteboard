package com.weedrice.whiteboard.domain.post.scheduled.repository;

import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduledPostRepository extends JpaRepository<ScheduledPost, Long> {

    interface ScheduledPostIdProjection {
        Long getScheduledPostId();
    }

    @EntityGraph(attributePaths = {"user", "board"})
    Optional<ScheduledPost> findByScheduledPostIdAndUser_UserId(Long scheduledPostId, Long userId);

    @EntityGraph(attributePaths = {"user", "board"})
    Page<ScheduledPost> findByUser_UserIdOrderByScheduledAtDescScheduledPostIdDesc(Long userId, Pageable pageable);

    @Query("""
            SELECT s.scheduledPostId AS scheduledPostId
            FROM ScheduledPost s
            WHERE s.status = 'SCHEDULED'
              AND s.scheduledAt <= :now
            ORDER BY s.scheduledAt ASC, s.scheduledPostId ASC
            """)
    List<ScheduledPostIdProjection> findDueScheduledPostIds(@Param("now") LocalDateTime now, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "board"})
    Optional<ScheduledPost> findByScheduledPostIdAndStatusAndProcessingStartedAt(
            Long scheduledPostId,
            String status,
            LocalDateTime processingStartedAt);

    @Modifying
    @Transactional
    @Query("""
            UPDATE ScheduledPost s
            SET s.status = 'PUBLISHING',
                s.processingStartedAt = :claimedAt,
                s.failureReason = null
            WHERE s.scheduledPostId = :scheduledPostId
              AND s.status = 'SCHEDULED'
              AND s.scheduledAt <= :now
            """)
    int claimForPublishing(
            @Param("scheduledPostId") Long scheduledPostId,
            @Param("now") LocalDateTime now,
            @Param("claimedAt") LocalDateTime claimedAt);

    @Modifying
    @Transactional
    @Query("""
            UPDATE ScheduledPost s
            SET s.status = 'PUBLISHED',
                s.publishedPostId = :publishedPostId,
                s.publishedAt = :publishedAt,
                s.processingStartedAt = null,
                s.failureReason = null
            WHERE s.scheduledPostId = :scheduledPostId
              AND s.status = 'PUBLISHING'
              AND s.processingStartedAt = :expectedProcessingStartedAt
            """)
    int markPublished(
            @Param("scheduledPostId") Long scheduledPostId,
            @Param("expectedProcessingStartedAt") LocalDateTime expectedProcessingStartedAt,
            @Param("publishedPostId") Long publishedPostId,
            @Param("publishedAt") LocalDateTime publishedAt);

    @Modifying
    @Transactional
    @Query("""
            UPDATE ScheduledPost s
            SET s.status = 'FAILED',
                s.failureReason = :failureReason,
                s.processingStartedAt = null
            WHERE s.scheduledPostId = :scheduledPostId
              AND s.status = 'PUBLISHING'
              AND s.processingStartedAt = :expectedProcessingStartedAt
            """)
    int markFailed(
            @Param("scheduledPostId") Long scheduledPostId,
            @Param("expectedProcessingStartedAt") LocalDateTime expectedProcessingStartedAt,
            @Param("failureReason") String failureReason);

    @Modifying
    @Transactional
    @Query("""
            UPDATE ScheduledPost s
            SET s.status = 'CANCELED',
                s.canceledAt = :canceledAt,
                s.processingStartedAt = null
            WHERE s.scheduledPostId = :scheduledPostId
              AND s.user.userId = :userId
              AND s.status IN ('SCHEDULED', 'FAILED')
            """)
    int cancelOwned(
            @Param("scheduledPostId") Long scheduledPostId,
            @Param("userId") Long userId,
            @Param("canceledAt") LocalDateTime canceledAt);
}
