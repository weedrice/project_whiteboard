package com.weedrice.whiteboard.domain.post.scheduled.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.post.dto.PostCreateResponse;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import com.weedrice.whiteboard.domain.post.scheduled.repository.ScheduledPostRepository;
import com.weedrice.whiteboard.domain.post.service.PostCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledPostPublishWorker {

    private static final int MAX_FAILURE_REASON_LENGTH = 255;

    private final ScheduledPostRepository scheduledPostRepository;
    private final PostCommandService postCommandService;
    private final ScheduledPostPayloadMapper payloadMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final ScheduledPostFileService scheduledPostFileService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(Long scheduledPostId, LocalDateTime now, LocalDateTime claimedAt) {
        return scheduledPostRepository.claimForPublishing(scheduledPostId, now, claimedAt) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishClaimed(Long scheduledPostId, LocalDateTime claimedAt) {
        ScheduledPost scheduledPost = loadClaimed(scheduledPostId, claimedAt);
        PostCreateResponse created = postCommandService.createPostWithResponse(
                scheduledPost.getUser().getUserId(),
                scheduledPost.getBoard().getBoardUrl(),
                payloadMapper.toPostCreateRequest(scheduledPost));
        scheduledPostFileService.removeReferences(scheduledPostId);
        int updated = scheduledPostRepository.markPublished(
                scheduledPostId, claimedAt, created.getPostId(), now());
        requireSingleLeaseUpdate(updated, scheduledPostId, "publish");
        publishSystemNotification(scheduledPost, "notification.scheduled.published");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long scheduledPostId, LocalDateTime claimedAt, RuntimeException exception) {
        ScheduledPost scheduledPost = loadClaimed(scheduledPostId, claimedAt);
        String reason = normalizeFailureReason(exception);
        int updated = scheduledPostRepository.markFailed(scheduledPostId, claimedAt, reason);
        requireSingleLeaseUpdate(updated, scheduledPostId, "fail");
        log.warn("Scheduled post publish failed. scheduledPostId={}, reason={}", scheduledPostId, reason);
        publishSystemNotification(scheduledPost, "notification.scheduled.failed");
    }

    private ScheduledPost loadClaimed(Long scheduledPostId, LocalDateTime claimedAt) {
        return scheduledPostRepository.findByScheduledPostIdAndStatusAndProcessingStartedAt(
                scheduledPostId,
                ScheduledPost.STATUS_PUBLISHING,
                claimedAt).orElseThrow(() -> new IllegalStateException(
                        "Scheduled post lease changed before transition: " + scheduledPostId));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String normalizeFailureReason(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        String normalized = message.strip();
        return normalized.length() <= MAX_FAILURE_REASON_LENGTH
                ? normalized
                : normalized.substring(0, MAX_FAILURE_REASON_LENGTH);
    }

    private void requireSingleLeaseUpdate(int updated, Long scheduledPostId, String transition) {
        if (updated != 1) {
            throw new IllegalStateException(
                    "Scheduled post lease changed before " + transition + ": " + scheduledPostId);
        }
    }

    private void publishSystemNotification(ScheduledPost scheduledPost, String messageKey) {
        eventPublisher.publishEvent(NotificationEvent.localized(
                scheduledPost.getUser(),
                null,
                NotificationType.SYSTEM,
                NotificationSourceType.SYSTEM,
                scheduledPost.getScheduledPostId(),
                messageKey,
                scheduledPost.getTitle()));
    }
}
