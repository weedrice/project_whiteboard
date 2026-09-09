package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.actor.UserIdRef;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DraftPostRepository extends JpaRepository<DraftPost, Long> {
    @EntityGraph(attributePaths = {"board", "originalPost"})
    @Query(value = """
            SELECT d
            FROM DraftPost d
            WHERE d.userId = :userId
              AND NOT EXISTS (
                  SELECT s.scheduledPostId
                  FROM ScheduledPost s
                  WHERE s.draftId = d.draftId
                    AND s.status IN ('SCHEDULED', 'PUBLISHING', 'FAILED')
              )
            ORDER BY d.modifiedAt DESC, d.draftId DESC
            """, countQuery = """
            SELECT COUNT(d)
            FROM DraftPost d
            WHERE d.userId = :userId
              AND NOT EXISTS (
                  SELECT s.scheduledPostId
                  FROM ScheduledPost s
                  WHERE s.draftId = d.draftId
                    AND s.status IN ('SCHEDULED', 'PUBLISHING', 'FAILED')
              )
            """)
    Page<DraftPost> findPageByUserWithBoard(@Param("userId") Long userId, Pageable pageable);
    default Page<DraftPost> findPageByUserWithBoard(UserIdRef user, Pageable pageable) {
        return findPageByUserWithBoard(user.getUserId(), pageable);
    }

    @Query("""
            SELECT d
            FROM DraftPost d
            WHERE d.userId = :userId
              AND d.board.boardUrl = :boardUrl
              AND ((:originalPostId IS NULL AND d.originalPost IS NULL)
                   OR d.originalPost.postId = :originalPostId)
              AND NOT EXISTS (
                  SELECT s.scheduledPostId
                  FROM ScheduledPost s
                  WHERE s.draftId = d.draftId
                    AND s.status IN ('SCHEDULED', 'PUBLISHING', 'FAILED')
              )
            ORDER BY d.modifiedAt DESC, d.draftId DESC
            """)
    List<DraftPost> findMatchingByUserAndTarget(
            @Param("userId") Long userId,
            @Param("boardUrl") String boardUrl,
            @Param("originalPostId") Long originalPostId,
            Pageable pageable);
    default List<DraftPost> findMatchingByUserAndTarget(
            UserIdRef user, String boardUrl, Long originalPostId, Pageable pageable) {
        return findMatchingByUserAndTarget(user.getUserId(), boardUrl, originalPostId, pageable);
    }

    @Query("""
            SELECT d
            FROM DraftPost d
            WHERE d.userId = :userId
              AND d.clientDraftKey = :clientDraftKey
              AND d.board.boardUrl = :boardUrl
              AND ((:originalPostId IS NULL AND d.originalPost IS NULL)
                   OR d.originalPost.postId = :originalPostId)
              AND NOT EXISTS (
                  SELECT s.scheduledPostId
                  FROM ScheduledPost s
                  WHERE s.draftId = d.draftId
                    AND s.status IN ('SCHEDULED', 'PUBLISHING', 'FAILED')
              )
            """)
    Optional<DraftPost> findRecoverableByUserAndClientDraftKeyAndTarget(
            @Param("userId") Long userId,
            @Param("clientDraftKey") String clientDraftKey,
            @Param("boardUrl") String boardUrl,
            @Param("originalPostId") Long originalPostId);
    default Optional<DraftPost> findRecoverableByUserAndClientDraftKeyAndTarget(
            UserIdRef user, String clientDraftKey, String boardUrl, Long originalPostId) {
        return findRecoverableByUserAndClientDraftKeyAndTarget(
                user.getUserId(), clientDraftKey, boardUrl, originalPostId);
    }

    Optional<DraftPost> findByDraftIdAndUserId(Long draftId, Long userId);
    default Optional<DraftPost> findByDraftIdAndUser(Long draftId, UserIdRef user) {
        return findByDraftIdAndUserId(draftId, user.getUserId());
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DraftPost d WHERE d.userId = :userId AND d.clientDraftKey = :clientDraftKey")
    Optional<DraftPost> findByUserAndClientDraftKeyForUpdate(
            @Param("userId") Long userId,
            @Param("clientDraftKey") String clientDraftKey);
    default Optional<DraftPost> findByUserAndClientDraftKeyForUpdate(
            UserIdRef user, String clientDraftKey) {
        return findByUserAndClientDraftKeyForUpdate(user.getUserId(), clientDraftKey);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DraftPost d WHERE d.draftId = :draftId AND d.userId = :userId")
    Optional<DraftPost> findByDraftIdAndUserForUpdate(@Param("draftId") Long draftId, @Param("userId") Long userId);
    default Optional<DraftPost> findByDraftIdAndUserForUpdate(Long draftId, UserIdRef user) {
        return findByDraftIdAndUserForUpdate(draftId, user.getUserId());
    }

    @Query("""
            SELECT COUNT(d)
            FROM DraftPost d
            WHERE d.userId = :userId
              AND NOT EXISTS (
                  SELECT s.scheduledPostId
                  FROM ScheduledPost s
                  WHERE s.draftId = d.draftId
                    AND s.status IN ('SCHEDULED', 'PUBLISHING', 'FAILED')
              )
            """)
    long countDeletableByUser(@Param("userId") Long userId);
    default long countDeletableByUser(UserIdRef user) {
        return countDeletableByUser(user.getUserId());
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT d
            FROM DraftPost d
            WHERE d.userId = :userId
              AND NOT EXISTS (
                  SELECT s.scheduledPostId
                  FROM ScheduledPost s
                  WHERE s.draftId = d.draftId
                    AND s.status IN ('SCHEDULED', 'PUBLISHING', 'FAILED')
              )
            ORDER BY d.modifiedAt ASC, d.draftId ASC
            """)
    List<DraftPost> findOldestByUser(@Param("userId") Long userId, Pageable pageable);
    default List<DraftPost> findOldestByUser(UserIdRef user, Pageable pageable) {
        return findOldestByUser(user.getUserId(), pageable);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT d
            FROM DraftPost d
            WHERE d.modifiedAt < :cutoff
              AND NOT EXISTS (
                  SELECT s.scheduledPostId
                  FROM ScheduledPost s
                  WHERE s.draftId = d.draftId
                    AND s.status IN ('SCHEDULED', 'PUBLISHING', 'FAILED')
              )
            ORDER BY d.modifiedAt ASC, d.draftId ASC
            """)
    List<DraftPost> findExpiredBefore(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    void deleteByBoard(Board board);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE DraftPost d SET d.series = null WHERE d.series.seriesId = :seriesId")
    int clearSeriesReference(@Param("seriesId") Long seriesId);
}
