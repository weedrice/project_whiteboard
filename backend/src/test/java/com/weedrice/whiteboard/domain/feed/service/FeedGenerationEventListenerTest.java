package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.feed.event.PostPublishedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FeedGenerationEventListenerTest {

    @InjectMocks
    private FeedGenerationEventListener feedGenerationEventListener;

    @Mock
    private FeedGenerationJobService feedGenerationJobService;

    @Test
    @DisplayName("게시글 발행 이벤트는 피드 생성 작업을 저장한다")
    void enqueuePostPublished_savesFeedGenerationJob() {
        PostPublishedEvent event = new PostPublishedEvent(100L, 10L);

        feedGenerationEventListener.enqueuePostPublished(event);

        verify(feedGenerationJobService).enqueuePostPublishedJob(100L, 10L);
    }

    @Test
    @DisplayName("커밋 이후 게시글 발행 이벤트는 피드 생성 작업을 즉시 처리한다")
    void handlePostPublished_processesFeedGenerationJob() {
        PostPublishedEvent event = new PostPublishedEvent(100L, 10L);

        feedGenerationEventListener.handlePostPublished(event);

        verify(feedGenerationJobService).processPostPublishedJob(100L);
    }

    @Test
    @DisplayName("피드 생성 작업 즉시 처리 중 예외가 나도 후속 처리는 예외를 던지지 않는다")
    void handlePostPublished_swallowsGenerationFailure() {
        PostPublishedEvent event = new PostPublishedEvent(100L, 10L);
        doThrow(new IllegalStateException("feed failure"))
                .when(feedGenerationJobService)
                .processPostPublishedJob(100L);

        feedGenerationEventListener.handlePostPublished(event);

        verify(feedGenerationJobService).processPostPublishedJob(100L);
    }
}
