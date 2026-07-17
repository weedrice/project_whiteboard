package com.weedrice.whiteboard.domain.board.repository;

import java.time.LocalDateTime;

public interface BoardVisitRepositoryCustom {
    int upsert(Long userId, String boardUrl, LocalDateTime visitedAt);
}
