package com.weedrice.whiteboard.domain.board.repository;

import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BoardCategoryRepository extends JpaRepository<BoardCategory, Long> {

    List<BoardCategory> findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(Long boardId, Boolean isActive);

    @Query("SELECT c.board.boardId FROM BoardCategory c WHERE c.categoryId = :categoryId")
    Optional<Long> findBoardIdByCategoryId(@Param("categoryId") Long categoryId);

    boolean existsByBoard_BoardIdAndNameAndIsActive(Long boardId, String name, Boolean isActive);

    boolean existsByBoard_BoardIdAndNameAndIsActiveAndCategoryIdNot(
            Long boardId,
            String name,
            Boolean isActive,
            Long categoryId);

    @Query("SELECT COALESCE(MAX(c.sortOrder), 0) FROM BoardCategory c "
            + "WHERE c.board.boardId = :boardId AND c.isActive = true")
    int findMaxActiveSortOrder(@Param("boardId") Long boardId);

    @EntityGraph(attributePaths = "board")
    Optional<BoardCategory> findByCategoryIdAndBoard_BoardIdAndIsActive(Long categoryId, Long boardId, Boolean isActive);

    @EntityGraph(attributePaths = "board")
    List<BoardCategory> findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(Collection<Long> boardIds, Boolean isActive);
}
