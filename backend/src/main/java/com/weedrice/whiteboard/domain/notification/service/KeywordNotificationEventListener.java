package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.feed.event.PostPublishedEvent;
import com.weedrice.whiteboard.domain.notification.repository.KeywordNotificationFanoutJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class KeywordNotificationEventListener {
    private final KeywordNotificationFanoutJobRepository jobRepository;
    private final Clock clock;

    @Order(Ordered.LOWEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void enqueuePostPublished(PostPublishedEvent event) {
        jobRepository.enqueueIfAbsent(event.postId(), LocalDateTime.now(clock));
    }
}
