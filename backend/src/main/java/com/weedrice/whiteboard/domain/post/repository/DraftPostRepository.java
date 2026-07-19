package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.user.entity.User;
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
    @EntityGraph(attributePaths = "board")
    @Query(value = """
            SELECT d
            FROM DraftPost d
            WHERE d.user = :user
            ORDER BY d.modifiedAt DESC, d.draftId DESC
            """, countQuery = """
            SELECT COUNT(d)
            FROM DraftPost d
            WHERE d.user = :user
            """)
    Page<DraftPost> findPageByUserWithBoard(@Param("user") User user, Pageable pageable);

    Optional<DraftPost> findByDraftIdAndUser(Long draftId, User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DraftPost d WHERE d.draftId = :draftId AND d.user = :user")
    Optional<DraftPost> findByDraftIdAndUserForUpdate(@Param("draftId") Long draftId, @Param("user") User user);

    long countByUser(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT d
            FROM DraftPost d
            WHERE d.user = :user
              AND NOT EXISTS (
                  SELECT s.scheduledPostId
                  FROM ScheduledPost s
                  WHERE s.draftId = d.draftId
                    AND s.status IN ('SCHEDULED', 'PUBLISHING', 'FAILED')
              )
            ORDER BY d.modifiedAt ASC, d.draftId ASC
            """)
    List<DraftPost> findOldestByUser(@Param("user") User user, Pageable pageable);

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
