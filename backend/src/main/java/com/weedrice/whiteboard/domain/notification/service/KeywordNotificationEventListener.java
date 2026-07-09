package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.feed.event.PostPublishedEvent;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.UserKeywordSubscription;
import com.weedrice.whiteboard.domain.notification.repository.UserKeywordSubscriptionRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class KeywordNotificationEventListener {

    private static final int COOLDOWN_MINUTES = 10;

    private final PostRepository postRepository;
    private final UserKeywordSubscriptionRepository keywordSubscriptionRepository;
    private final NotificationCommandService notificationCommandService;
    private final UserBlockService userBlockService;

    @Order(Ordered.LOWEST_PRECEDENCE)
    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handlePostPublished(PostPublishedEvent event) {
        try {
            postRepository.findByIdWithRelations(event.postId())
                    .filter(this::isKeywordNotifiable)
                    .ifPresent(this::notifyMatchingSubscribers);
        } catch (Exception ex) {
            log.warn("Failed to publish keyword notifications. postId={}", event.postId(), ex);
        }
    }

    private boolean isKeywordNotifiable(Post post) {
        return !Boolean.TRUE.equals(post.getIsDeleted())
                && !Boolean.TRUE.equals(post.getIsSecret())
                && post.getBoard() != null
                && Boolean.TRUE.equals(post.getBoard().getIsPublic());
    }

    private void notifyMatchingSubscribers(Post post) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cooldownThreshold = now.minusMinutes(COOLDOWN_MINUTES);
        for (UserKeywordSubscription subscription : keywordSubscriptionRepository.findMatchingTitle(post.getTitle())) {
            if (isAuthor(subscription, post)
                    || isBlocked(subscription, post)
                    || isCoolingDown(subscription, cooldownThreshold)) {
                continue;
            }
            NotificationEvent notificationEvent = new NotificationEvent(
                    subscription.getUser(),
                    post.getUser(),
                    post.getAgent(),
                    NotificationType.KEYWORD,
                    NotificationSourceType.POST,
                    post.getPostId(),
                    post.getTitle());
            if (notificationCommandService.handleNotificationEvent(notificationEvent) != null) {
                subscription.markNotified(now);
            }
        }
    }

    private boolean isAuthor(UserKeywordSubscription subscription, Post post) {
        return subscription.getUser() != null
                && post.getUser() != null
                && subscription.getUser().getUserId().equals(post.getUser().getUserId());
    }

    private boolean isCoolingDown(UserKeywordSubscription subscription, LocalDateTime cooldownThreshold) {
        return subscription.getLastNotifiedAt() != null
                && subscription.getLastNotifiedAt().isAfter(cooldownThreshold);
    }

    private boolean isBlocked(UserKeywordSubscription subscription, Post post) {
        if (subscription.getUser() == null || subscription.getUser().getUserId() == null
                || post.getUser() == null || post.getUser().getUserId() == null) {
            return false;
        }
        return userBlockService.isEitherDirectionBlocked(
                subscription.getUser().getUserId(),
                post.getUser().getUserId());
    }
}
