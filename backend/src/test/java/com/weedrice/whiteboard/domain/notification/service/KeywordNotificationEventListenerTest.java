package com.weedrice.whiteboard.domain.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordNotificationEventListenerTest {

    @Test
    void handlePostPublished_runsAsyncAfterCommitInNewTransaction() throws NoSuchMethodException {
        Method method = KeywordNotificationEventListener.class.getMethod(
                "handlePostPublished",
                com.weedrice.whiteboard.domain.feed.event.PostPublishedEvent.class);

        Async async = method.getAnnotation(Async.class);
        TransactionalEventListener listener = method.getAnnotation(TransactionalEventListener.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(async).isNotNull();
        assertThat(async.value()).isEqualTo("taskExecutor");
        assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
