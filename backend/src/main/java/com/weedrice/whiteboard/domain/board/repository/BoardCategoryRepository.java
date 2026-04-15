package com.weedrice.whiteboard.domain.board.repository;

import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BoardCategoryRepository extends JpaRepository<BoardCategory, Long> {

    List<BoardCategory> findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(Long boardId, Boolean isActive);

    @EntityGraph(attributePaths = "board")
    List<BoardCategory> findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(Collection<Long> boardIds, Boolean isActive);
}
