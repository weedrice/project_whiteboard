package com.weedrice.whiteboard.domain.board.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {

    List<Board> findByIsActiveOrderBySortOrderAsc(Boolean isActive);

    List<Board> findByBoardNameContainingIgnoreCaseAndIsActiveTrue(String keyword);

    boolean existsByBoardName(String boardName);

    boolean existsByBoardUrl(String boardUrl); // 추가

    Optional<Board> findByBoardUrl(String boardUrl); // 추가

    @Query("SELECT p.board FROM Post p WHERE p.isDeleted = false AND p.board.isActive = true GROUP BY p.board ORDER BY COUNT(p) DESC")
    List<Board> findTopBoardsByPostCount(Pageable pageable);

    @Query("SELECT COALESCE(MAX(b.sortOrder), 0) FROM Board b")
    Integer findMaxSortOrder();

    Optional<Board> findByBoardId(Long boardId);
}
