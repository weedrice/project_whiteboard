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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SearchRecordEventPublisherTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("search record event keyword is capped at 255 characters")
    void publish_truncatesKeyword() {
        SearchRecordEventPublisher publisher = new SearchRecordEventPublisher(eventPublisher);
        String keyword = "A".repeat(SearchRequestNormalizer.MAX_KEYWORD_LENGTH + 10);

        publisher.publish(1L, keyword);

        ArgumentCaptor<SearchRecordedEvent> eventCaptor = ArgumentCaptor.forClass(SearchRecordedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().keyword())
                .hasSize(SearchRequestNormalizer.MAX_KEYWORD_LENGTH)
                .isEqualTo("A".repeat(SearchRequestNormalizer.MAX_KEYWORD_LENGTH));
    }
}
