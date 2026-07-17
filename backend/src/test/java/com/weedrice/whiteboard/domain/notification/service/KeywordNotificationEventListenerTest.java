package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.feed.event.PostPublishedEvent;
import com.weedrice.whiteboard.domain.notification.entity.KeywordNotificationFanoutJob;
import com.weedrice.whiteboard.domain.notification.repository.KeywordNotificationFanoutJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KeywordNotificationEventListenerTest {
    @Test
    void enqueue_isBeforeCommitAndPersistsOneDurableFanoutJob() throws Exception {
        Method method = KeywordNotificationEventListener.class.getMethod(
                "enqueuePostPublished", PostPublishedEvent.class);
        assertThat(method.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.BEFORE_COMMIT);

        KeywordNotificationFanoutJobRepository repository = mock(KeywordNotificationFanoutJobRepository.class);
        KeywordNotificationEventListener listener = new KeywordNotificationEventListener(
                repository, Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC));

        listener.enqueuePostPublished(new PostPublishedEvent(100L, 10L));

        verify(repository).enqueueIfAbsent(eq(100L), any());
    }

    @Test
    void enqueue_isIdempotentForExistingPostJob() {
        KeywordNotificationFanoutJobRepository repository = mock(KeywordNotificationFanoutJobRepository.class);
        KeywordNotificationEventListener listener = new KeywordNotificationEventListener(repository, Clock.systemUTC());

        listener.enqueuePostPublished(new PostPublishedEvent(100L, 10L));
        listener.enqueuePostPublished(new PostPublishedEvent(100L, 10L));

        verify(repository, times(2)).enqueueIfAbsent(eq(100L), any());
        verify(repository, never()).save(any());
    }
}
