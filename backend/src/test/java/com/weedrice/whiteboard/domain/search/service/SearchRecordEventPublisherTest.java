package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.search.event.SearchRecordedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchRecordEventPublisherTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private RecentSearchCommandService recentSearchCommandService;

    @Test
    @DisplayName("search record event keyword is capped at 255 characters")
    void publish_truncatesKeyword() {
        SearchRecordEventPublisher publisher = new SearchRecordEventPublisher(eventPublisher, recentSearchCommandService);
        String keyword = "A".repeat(SearchRequestNormalizer.MAX_KEYWORD_LENGTH + 10);

        publisher.publish(1L, keyword);

        ArgumentCaptor<SearchRecordedEvent> eventCaptor = ArgumentCaptor.forClass(SearchRecordedEvent.class);
        var order = inOrder(recentSearchCommandService, eventPublisher);
        order.verify(recentSearchCommandService).recordRecentSearch(1L, "A".repeat(SearchRequestNormalizer.MAX_KEYWORD_LENGTH));
        order.verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().keyword())
                .hasSize(SearchRequestNormalizer.MAX_KEYWORD_LENGTH)
                .isEqualTo("A".repeat(SearchRequestNormalizer.MAX_KEYWORD_LENGTH));
    }

    @Test
    void recentHistoryFailureStillQueuesStatistics() {
        doThrow(new IllegalStateException()).when(recentSearchCommandService).recordRecentSearch(1L, "query");
        new SearchRecordEventPublisher(eventPublisher, recentSearchCommandService).publish(1L, "query");

        verify(eventPublisher).publishEvent(any(SearchRecordedEvent.class));
    }

    @Test
    void anonymousSearchOnlyQueuesStatisticsAndToleratesRejection() {
        doThrow(new IllegalStateException()).when(eventPublisher).publishEvent(any(SearchRecordedEvent.class));
        new SearchRecordEventPublisher(eventPublisher, recentSearchCommandService).publish(null, "query");

        verifyNoInteractions(recentSearchCommandService);
        verify(eventPublisher).publishEvent(any(SearchRecordedEvent.class));
    }
}
