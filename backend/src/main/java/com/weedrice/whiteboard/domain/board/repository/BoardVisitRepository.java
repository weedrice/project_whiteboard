package com.weedrice.whiteboard.domain.board.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardVisit;
import com.weedrice.whiteboard.domain.board.entity.BoardVisitId;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface BoardVisitRepository extends JpaRepository<BoardVisit, BoardVisitId>, BoardVisitRepositoryCustom {
    interface BoardActivityProjection {
        Long getBoardId();
        LocalDateTime getLatestPostAt();
        Long getNewPostCount();
    }

    @Query("""
            SELECT p.board.boardId AS boardId,
                   MAX(p.createdAt) AS latestPostAt,
                   COALESCE(SUM(CASE
                       WHEN bv.lastVisitedAt IS NULL OR p.createdAt > bv.lastVisitedAt THEN 1L
                       ELSE 0L
                   END), 0L) AS newPostCount
            FROM Post p
            LEFT JOIN BoardVisit bv
              ON bv.board = p.board
             AND bv.user.userId = :userId
            WHERE p.board.boardId IN :boardIds
              AND p.isDeleted = false
              AND p.createdAt > :recentCutoff
              AND (
                    p.isSecret = false
                    OR p.user.userId = :userId
                    OR :isSuperAdmin = true
                    OR EXISTS (
                        SELECT admin.adminId
                        FROM Admin admin
                        WHERE admin.board = p.board
                          AND admin.user.userId = :userId
                          AND admin.isActive = true
                    )
              )
            GROUP BY p.board.boardId
            """)
    List<BoardActivityProjection> findActivityByBoardIds(
            @Param("boardIds") Collection<Long> boardIds,
            @Param("userId") Long userId,
            @Param("isSuperAdmin") boolean isSuperAdmin,
            @Param("recentCutoff") LocalDateTime recentCutoff);
}
