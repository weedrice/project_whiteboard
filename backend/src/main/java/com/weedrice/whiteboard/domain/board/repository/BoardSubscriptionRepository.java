package com.weedrice.whiteboard.domain.board.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscriptionId;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BoardSubscriptionRepository extends JpaRepository<BoardSubscription, BoardSubscriptionId> {
    interface BoardSubscriberCountProjection {
        Long getBoardId();
        long getSubscriberCount();
    }

    Page<BoardSubscription> findByUser(User user, Pageable pageable);

    Page<BoardSubscription> findByUserAndBoard_IsActiveOrderBySortOrderAsc(User user, Boolean isActive,
            Pageable pageable);

    List<BoardSubscription> findAllByUser(User user);

    @EntityGraph(attributePaths = "user")
    List<BoardSubscription> findAllByBoard(Board board);

    long countByBoard(Board board);
    long countByUser(User user);

    boolean existsByUserAndBoard(User user, Board board);

    void deleteByBoard(Board board);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(MAX(bs.sortOrder), 0) FROM BoardSubscription bs WHERE bs.user = :user")
    Integer findMaxSortOrder(@org.springframework.data.repository.query.Param("user") User user);

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
