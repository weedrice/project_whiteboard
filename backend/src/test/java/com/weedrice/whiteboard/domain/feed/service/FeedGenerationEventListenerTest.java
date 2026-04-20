package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.feed.event.PostPublishedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedGenerationEventListenerTest {

    @InjectMocks
    private FeedGenerationEventListener feedGenerationEventListener;

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private FeedGenerationService feedGenerationService;

    @Test
    @DisplayName("게시글 발행 이벤트는 게시판 구독자 피드 생성을 호출한다")
    void handlePostPublished_generatesSubscriberFeeds() {
        Board board = Board.builder().boardName("free").build();
        ReflectionTestUtils.setField(board, "boardId", 10L);
        PostPublishedEvent event = new PostPublishedEvent(100L, 10L);

        when(boardRepository.findById(10L)).thenReturn(Optional.of(board));

        feedGenerationEventListener.handlePostPublished(event);

        verify(feedGenerationService).generatePostFeeds(board, 100L);
    }

    @Test
    @DisplayName("피드 생성 중 예외가 나도 게시글 발행 후속 처리는 예외를 삼킨다")
    void handlePostPublished_swallowsGenerationFailure() {
        Board board = Board.builder().boardName("free").build();
        ReflectionTestUtils.setField(board, "boardId", 10L);
        PostPublishedEvent event = new PostPublishedEvent(100L, 10L);

        when(boardRepository.findById(10L)).thenReturn(Optional.of(board));
        doThrow(new IllegalStateException("feed failure"))
                .when(feedGenerationService)
                .generatePostFeeds(any(Board.class), any(Long.class));

        feedGenerationEventListener.handlePostPublished(event);

        verify(feedGenerationService).generatePostFeeds(board, 100L);
    }
}
