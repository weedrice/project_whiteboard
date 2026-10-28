package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.KeywordNotificationFanoutJob;
import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
import com.weedrice.whiteboard.domain.notification.entity.UserKeywordSubscription;
import com.weedrice.whiteboard.domain.notification.repository.KeywordNotificationFanoutJobRepository;
import com.weedrice.whiteboard.domain.notification.repository.NotificationDeliveryJobRepository;
import com.weedrice.whiteboard.domain.notification.repository.UserKeywordSubscriptionRepository;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class KeywordNotificationFanoutProcessor {
    private static final int BATCH_SIZE = 100;
    private static final int COOLDOWN_MINUTES = 10;
    private final KeywordNotificationFanoutJobRepository jobRepository;
    private final UserKeywordSubscriptionRepository subscriptionRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final AgentRepository agentRepository;
    private final NotificationDeliveryJobService deliveryJobService;
    private final NotificationDeliveryJobRepository deliveryJobRepository;
    private final UserBlockService userBlockService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final KeywordNotificationFanoutMetrics metrics;

    public int processDueJobs() {
        List<Long> ids = jobRepository.findDueIds(now(), PageRequest.of(0, BATCH_SIZE));
        int processed = 0;
        for (Long id : ids) {
            try {
                if (Boolean.TRUE.equals(transactionTemplate.execute(status -> processPage(id)))) processed++;
            } catch (RuntimeException exception) {
                transactionTemplate.executeWithoutResult(status -> fail(id, exception));
            }
        }
        LocalDateTime observedAt = now();
        metrics.update(
                jobRepository.countByStatus(KeywordNotificationFanoutJob.Status.PENDING),
                jobRepository.countByStatus(KeywordNotificationFanoutJob.Status.FAILED),
                jobRepository.findOldestPendingAttemptAt()
                        .map(value -> java.time.Duration.between(value, observedAt).getSeconds()).orElse(0L));
        return processed;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processPage(Long jobId) {
        KeywordNotificationFanoutJob job = jobRepository.findByIdForUpdate(jobId)
                .filter(candidate -> candidate.isDue(now())).orElse(null);
        if (job == null) return false;
        job.claim(now());
        Post post = postRepository.findByIdWithRelations(job.getPostId()).orElse(null);
        if (post == null || !isNotifiable(post)) {
            job.advance(job.getLastSubscriptionId(), true, now());
            return true;
        }
        List<UserKeywordSubscription> subscriptions = subscriptionRepository.findMatchingTitleAfter(
                post.getTitle(), job.getLastSubscriptionId(), PageRequest.of(0, BATCH_SIZE));
        Set<Long> blocked = resolveBlockedUserIds(post);
        User author = userRepository.findById(post.getUserId()).orElse(null);
        Agent authorAgent = post.getAgentId() == null ? null : agentRepository.findById(post.getAgentId()).orElse(null);
        LocalDateTime cooldown = now().minusMinutes(COOLDOWN_MINUTES);
        List<UserKeywordSubscription> eligible = subscriptions.stream()
                .filter(subscription -> isEligible(subscription, post, blocked, cooldown)).toList();
        Map<Long, UUID> existingEventIds = existingKeywordEventIds(post.getPostId(), eligible);
        for (UserKeywordSubscription subscription : eligible) {
            Long receiverId = subscription.getUser().getUserId();
            NotificationEvent event = NotificationEvent.localized(
                    existingEventIds.getOrDefault(receiverId, keywordEventId(post.getPostId(), receiverId)),
                    subscription.getUser(), author, authorAgent,
                    NotificationType.KEYWORD, NotificationSourceType.POST, post.getPostId(),
                    "notification.keyword.matched", post.getTitle());
            NotificationDeliveryJob deliveryJob = deliveryJobService.enqueue(event);
            if (deliveryJob != null && (subscription.getLastNotifiedAt() == null
                    || subscription.getLastNotifiedAt().isBefore(deliveryJob.getCreatedAt()))) {
                // A later page may match another keyword for the same receiver. Reuse the
                // first enqueue time so a duplicate neither extends nor rewinds cooldown.
                subscription.markNotified(deliveryJob.getCreatedAt());
            }
        }
        Long cursor = subscriptions.isEmpty() ? job.getLastSubscriptionId()
                : subscriptions.get(subscriptions.size() - 1).getSubscriptionId();
        job.advance(cursor, subscriptions.size() < BATCH_SIZE, now());
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long jobId, RuntimeException exception) {
        jobRepository.findByIdForUpdate(jobId).ifPresent(job ->
                job.fail(exception.getClass().getSimpleName(), now().plusMinutes(1)));
        log.warn("Keyword notification fan-out failed. jobId={}, exceptionType={}",
                jobId, exception.getClass().getSimpleName());
        metrics.recordRetry();
    }

    private boolean isNotifiable(Post post) {
        return !Boolean.TRUE.equals(post.getIsDeleted())
                && !Boolean.TRUE.equals(post.getIsSecret())
                && !Boolean.TRUE.equals(post.getIsBlinded())
                && post.getBoard() != null
                && Boolean.TRUE.equals(post.getBoard().getIsActive())
                && Boolean.TRUE.equals(post.getBoard().getIsPublic())
                && Boolean.TRUE.equals(post.getBoard().getIsListed());
    }

    private UUID keywordEventId(Long postId, Long receiverId) {
        // Persisted event identity survives page boundaries and worker restarts.
        return UUID.nameUUIDFromBytes(("keyword-post:" + postId + ":receiver:" + receiverId)
                .getBytes(StandardCharsets.UTF_8));
    }

    private Map<Long, UUID> existingKeywordEventIds(Long postId, List<UserKeywordSubscription> eligible) {
        List<Long> receiverIds = eligible.stream().map(subscription -> subscription.getUser().getUserId())
                .distinct().toList();
        if (receiverIds.isEmpty()) return Map.of();
        // One lookup per page also finds random-ID events from before this change.
        // The fan-out job lock serializes pages; ordered results retain each receiver's first event.
        Map<Long, UUID> eventIds = new HashMap<>();
        for (NotificationDeliveryJob existing : deliveryJobRepository.findKeywordPostJobsForReceivers(
                postId, receiverIds)) {
            eventIds.putIfAbsent(existing.getReceiverUserId(), existing.getEventId());
        }
        return eventIds;
    }
    private boolean isEligible(UserKeywordSubscription s, Post p, Set<Long> blocked, LocalDateTime cooldown) {
        Long userId = s.getUser() == null ? null : s.getUser().getUserId();
        return userId != null
                && !userId.equals(p.getUserId())
                && !blocked.contains(userId)
                && (s.getLastNotifiedAt() == null || !s.getLastNotifiedAt().isAfter(cooldown));
    }
    private Set<Long> resolveBlockedUserIds(Post post) {
        if (post.getUserId() == null) return Set.of();
        return Set.copyOf(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(post.getUserId()));
    }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
}
