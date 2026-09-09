package com.weedrice.whiteboard.domain.post.scheduled.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.post.dto.PostCreateResponse;
import com.weedrice.whiteboard.domain.post.port.PostNotificationPort;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import com.weedrice.whiteboard.domain.post.scheduled.repository.ScheduledPostRepository;
import com.weedrice.whiteboard.domain.post.service.PostCommandService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledPostPublishWorker {

    static final String FAILURE_CODE_BUSINESS = "PUBLISH_BUSINESS_REJECTED";
    static final String FAILURE_CODE_INTERNAL = "PUBLISH_INTERNAL_ERROR";

    private final ScheduledPostRepository scheduledPostRepository;
    private final PostCommandService postCommandService;
    private final ScheduledPostPayloadMapper payloadMapper;
    private final PostNotificationPort postNotificationPort;
    private final Clock clock;
    private final ScheduledPostFileService scheduledPostFileService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(Long scheduledPostId, LocalDateTime now, LocalDateTime claimedAt) {
        return scheduledPostRepository.claimForPublishing(scheduledPostId, now, claimedAt) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishClaimed(Long scheduledPostId, LocalDateTime claimedAt) {
        ScheduledPost scheduledPost = loadClaimed(scheduledPostId, claimedAt);
        PostCreateResponse created = postCommandService.createScheduledPostWithResponse(
                scheduledPost.getUserId(),
                scheduledPost.getBoard().getBoardUrl(),
                payloadMapper.toPostCreateRequest(scheduledPost),
                scheduledPostId);
        scheduledPostFileService.removeReferences(scheduledPostId);
        int updated = scheduledPostRepository.markPublished(
                scheduledPostId, claimedAt, created.getPostId(), now());
        requireSingleLeaseUpdate(updated, scheduledPostId, "publish");
        publishNotification(
                scheduledPost,
                NotificationSourceType.POST,
                created.getPostId(),
                "notification.scheduled.published");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long scheduledPostId, LocalDateTime claimedAt, RuntimeException exception) {
        ScheduledPost scheduledPost = loadClaimed(scheduledPostId, claimedAt);
        String reason = normalizeFailureReason(exception);
        int updated = scheduledPostRepository.markFailed(scheduledPostId, claimedAt, reason);
        requireSingleLeaseUpdate(updated, scheduledPostId, "fail");
        log.warn("Scheduled post publish failed. scheduledPostId={}, failureCode={}, exceptionType={}",
                scheduledPostId, reason, exception.getClass().getSimpleName());
        publishNotification(
                scheduledPost,
                NotificationSourceType.SYSTEM,
                scheduledPost.getScheduledPostId(),
                "notification.scheduled.failed");
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
        return exception instanceof BusinessException
                ? FAILURE_CODE_BUSINESS
                : FAILURE_CODE_INTERNAL;
    }

    private void requireSingleLeaseUpdate(int updated, Long scheduledPostId, String transition) {
        if (updated != 1) {
            throw new IllegalStateException(
                    "Scheduled post lease changed before " + transition + ": " + scheduledPostId);
        }
    }

    private void publishNotification(
            ScheduledPost scheduledPost,
            NotificationSourceType sourceType,
            Long sourceId,
            String messageKey) {
        postNotificationPort.publishSystemNotification(
                scheduledPost.getUserId(),
                sourceType,
                sourceId,
                messageKey,
                scheduledPost.getTitle());
    }
}
