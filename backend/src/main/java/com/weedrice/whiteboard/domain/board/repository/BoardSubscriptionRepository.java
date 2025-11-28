package com.weedrice.whiteboard.domain.board.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscriptionId;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardSubscriptionRepository extends JpaRepository<BoardSubscription, BoardSubscriptionId> {
    Page<BoardSubscription> findByUser(User user, Pageable pageable);
    long countByBoard(Board board);
    boolean existsByUserAndBoard(User user, Board board);
    void deleteByBoard(Board board);
}
