package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.repository.BoardVisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
class BoardVisitWriter {

    private final BoardVisitRepository boardVisitRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsert(Long userId, String boardUrl, LocalDateTime visitedAt) {
        boardVisitRepository.upsert(userId, boardUrl, visitedAt);
    }
}
