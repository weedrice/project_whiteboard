package com.weedrice.whiteboard.domain.board.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {

    List<Board> findByIsActiveOrderBySortOrderAsc(String isActive);
    boolean existsByBoardName(String boardName);
    boolean existsByBoardUrl(String boardUrl); // 추가
    Optional<Board> findByBoardUrl(String boardUrl); // 추가

    @Query("SELECT p.board FROM Post p WHERE p.isDeleted = 'N' GROUP BY p.board ORDER BY COUNT(p) DESC")
    List<Board> findTopBoardsByPostCount(Pageable pageable);
}
