package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.feed.event.PostPublishedEvent;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class FeedGenerationEventListener {

    private final BoardRepository boardRepository;
    private final FeedGenerationService feedGenerationService;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handlePostPublished(PostPublishedEvent event) {
        try {
            Board board = boardRepository.findById(event.boardId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
            feedGenerationService.generatePostFeeds(board, event.postId());
        } catch (Exception ex) {
            log.error("Failed to generate feeds after post publish. postId={}, boardId={}",
                    event.postId(), event.boardId(), ex);
        }
    }
}
