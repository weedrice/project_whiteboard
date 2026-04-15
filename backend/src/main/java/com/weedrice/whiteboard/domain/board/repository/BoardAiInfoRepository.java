package com.weedrice.whiteboard.domain.board.repository;

import com.weedrice.whiteboard.domain.board.entity.BoardAiInfo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BoardAiInfoRepository extends JpaRepository<BoardAiInfo, Long> {

    Optional<BoardAiInfo> findByBoard_BoardId(Long boardId);

    @EntityGraph(attributePaths = "board")
    List<BoardAiInfo> findByBoard_BoardIdIn(Collection<Long> boardIds);
}
