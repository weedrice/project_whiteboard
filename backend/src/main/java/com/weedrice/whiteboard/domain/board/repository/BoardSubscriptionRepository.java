package com.weedrice.whiteboard.domain.board.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscriptionId;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BoardSubscriptionRepository extends JpaRepository<BoardSubscription, BoardSubscriptionId> {
    interface BoardSubscriberCountProjection {
        Long getBoardId();
        long getSubscriberCount();
    }

    Page<BoardSubscription> findByUser(User user, Pageable pageable);

    @EntityGraph(attributePaths = {"board", "board.creator"})
    @Query("""
            SELECT bs
            FROM BoardSubscription bs
            WHERE bs.user = :user
            ORDER BY bs.sortOrder ASC, bs.createdAt ASC, bs.board.boardId ASC
            """)
    Page<BoardSubscription> findByUserOrderBySortOrderAsc(@Param("user") User user, Pageable pageable);

    @EntityGraph(attributePaths = "board")
    @Query("""
            SELECT bs
            FROM BoardSubscription bs
            WHERE bs.user = :user
              AND bs.board.isActive = :isActive
            ORDER BY bs.sortOrder ASC, bs.createdAt ASC, bs.board.boardId ASC
            """)
    Page<BoardSubscription> findByUserAndBoard_IsActiveOrderBySortOrderAsc(@Param("user") User user,
            @Param("isActive") Boolean isActive,
            Pageable pageable);

    List<BoardSubscription> findAllByUser(User user);

    @EntityGraph(attributePaths = "board")
    @Query("""
            SELECT bs
            FROM BoardSubscription bs
            WHERE bs.user = :user
            ORDER BY bs.sortOrder ASC, bs.createdAt ASC, bs.board.boardId ASC
            """)
    List<BoardSubscription> findAllByUserOrderBySortOrderAsc(@Param("user") User user);

    @EntityGraph(attributePaths = "board")
    @Query("""
            SELECT bs
            FROM BoardSubscription bs
            WHERE bs.user = :user
              AND bs.board.isActive = true
            ORDER BY bs.sortOrder ASC, bs.createdAt ASC, bs.board.boardId ASC
            """)
    List<BoardSubscription> findAllByUserAndBoard_IsActiveTrueOrderBySortOrderAsc(@Param("user") User user);

    @EntityGraph(attributePaths = {"board", "board.creator"})
    @Query("""
            SELECT bs
            FROM BoardSubscription bs
            JOIN bs.board b
            WHERE bs.user = :user
              AND b.isActive = true
              AND b.boardUrl IN :boardUrls
              AND (
                    :isSuperAdmin = true
                    OR EXISTS (
                        SELECT admin.adminId
                        FROM Admin admin
                        WHERE admin.board = b
                          AND admin.user = :user
                          AND admin.isActive = true
                    )
                    OR b.isPublic = true
                  )
            ORDER BY bs.sortOrder ASC, bs.createdAt ASC, bs.board.boardId ASC
            """)
    List<BoardSubscription> findReorderableByUserAndBoardUrlIn(@Param("user") User user,
            @Param("boardUrls") Collection<String> boardUrls,
            @Param("isSuperAdmin") boolean isSuperAdmin);

    @EntityGraph(attributePaths = {"board", "board.creator"})
    @Query(value = """
            SELECT bs
            FROM BoardSubscription bs
            JOIN bs.board b
            WHERE bs.user = :user
              AND (
                    :isSuperAdmin = true
                    OR EXISTS (
                        SELECT admin.adminId
                        FROM Admin admin
                        WHERE admin.board = b
                          AND admin.user = :user
                          AND admin.isActive = true
                    )
                    OR (b.isActive = true AND b.isPublic = true)
                  )
            ORDER BY bs.sortOrder ASC, bs.createdAt ASC, bs.board.boardId ASC
            """,
            countQuery = """
            SELECT COUNT(bs)
            FROM BoardSubscription bs
            JOIN bs.board b
            WHERE bs.user = :user
              AND (
                    :isSuperAdmin = true
                    OR EXISTS (
                        SELECT admin.adminId
                        FROM Admin admin
                        WHERE admin.board = b
                          AND admin.user = :user
                          AND admin.isActive = true
                    )
                    OR (b.isActive = true AND b.isPublic = true)
                  )
            """)
    Page<BoardSubscription> findVisibleByUserOrderBySortOrderAsc(@Param("user") User user,
            @Param("isSuperAdmin") boolean isSuperAdmin,
            Pageable pageable);

    @EntityGraph(attributePaths = "user")
    List<BoardSubscription> findAllByBoard(Board board);

    long countByBoard(Board board);
    long countByUser(User user);

    boolean existsByUserAndBoard(User user, Board board);

    void deleteByBoard(Board board);

    @Query("""
            SELECT COALESCE(MAX(bs.sortOrder), 0)
            FROM BoardSubscription bs
            WHERE bs.user = :user
            """)
    Integer findMaxSortOrder(@Param("user") User user);

    @EntityGraph(attributePaths = "board")
    List<BoardSubscription> findByUserAndBoardIn(User user, List<Board> boards);

    @org.springframework.data.jpa.repository.Query("""
            SELECT bs.board.boardId AS boardId, COUNT(bs) AS subscriberCount
            FROM BoardSubscription bs
            WHERE bs.board.boardId IN :boardIds
            GROUP BY bs.board.boardId
            """)
    List<BoardSubscriberCountProjection> countByBoardIds(
            @org.springframework.data.repository.query.Param("boardIds") Collection<Long> boardIds);
}
