package com.weedrice.whiteboard.domain.board.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {

    @EntityGraph(attributePaths = "creator")
    List<Board> findByIsActiveOrderBySortOrderAsc(Boolean isActive);

    @EntityGraph(attributePaths = "creator")
    List<Board> findByIsActiveAndIsPublicOrderBySortOrderAsc(Boolean isActive, Boolean isPublic);

    List<Board> findByBoardNameContainingIgnoreCaseAndIsActiveTrue(String keyword);

    List<Board> findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc(String keyword,
            Pageable pageable);

    boolean existsByBoardName(String boardName);

    boolean existsByBoardUrl(String boardUrl);

    @EntityGraph(attributePaths = "creator")
    Optional<Board> findByBoardUrl(String boardUrl);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Board b JOIN FETCH b.creator WHERE b.boardUrl = :boardUrl")
    Optional<Board> findByBoardUrlForUpdate(@Param("boardUrl") String boardUrl);

    @EntityGraph(attributePaths = "creator")
    @Query("""
            SELECT b
            FROM Post p
            JOIN p.board b
            WHERE p.isDeleted = false
              AND b.isActive = true
            GROUP BY b
            ORDER BY COUNT(p) DESC, b.sortOrder ASC, b.boardId ASC
            """)
    List<Board> findTopBoardsByPostCount(Pageable pageable);

    @EntityGraph(attributePaths = "creator")
    @Query("""
            SELECT b
            FROM Post p
            JOIN p.board b
            WHERE p.isDeleted = false
              AND b.isActive = true
              AND b.isPublic = true
              AND LOWER(b.boardUrl) <> 'inquiry'
            GROUP BY b
            ORDER BY COUNT(p) DESC, b.sortOrder ASC, b.boardId ASC
            """)
    List<Board> findTopPublicBoardsByPostCount(Pageable pageable);

    @EntityGraph(attributePaths = "creator")
    List<Board> findAllByOrderBySortOrderAsc();

    @Query("SELECT COALESCE(MAX(b.sortOrder), 0) FROM Board b")
    Integer findMaxSortOrder();

    @EntityGraph(attributePaths = "creator")
    Optional<Board> findByBoardId(Long boardId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Board b WHERE b.boardId = :boardId")
    Optional<Board> findByIdForUpdate(@Param("boardId") Long boardId);
}
