package com.weedrice.whiteboard.domain.post.scheduled.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.util.BoardUrlNormalizer;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.post.dto.PostCreateResponse;
import com.weedrice.whiteboard.domain.post.scheduled.dto.ScheduledPostRequest;
import com.weedrice.whiteboard.domain.post.scheduled.dto.ScheduledPostResponse;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import com.weedrice.whiteboard.domain.post.scheduled.repository.ScheduledPostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAuthorCommandPolicy;
import com.weedrice.whiteboard.domain.post.service.PostCommandService;
import com.weedrice.whiteboard.domain.post.service.PostTitleValidator;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduledPostService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int PUBLISH_BATCH_SIZE = 20;
    private static final int MIN_SCHEDULE_MINUTES = 5;
    private static final int MAX_SCHEDULE_DAYS = 30;
    private static final int MAX_FAILURE_REASON_LENGTH = 255;
    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("scheduledAt"),
            Sort.Order.desc("scheduledPostId"));

    private final ScheduledPostRepository scheduledPostRepository;
    private final BoardRepository boardRepository;
    private final UserWritableResolver userWritableResolver;
    private final SanctionService sanctionService;
    private final PostAuthorCommandPolicy postAuthorCommandPolicy;
    private final PostCommandService postCommandService;
    private final ScheduledPostPayloadMapper payloadMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public ScheduledPostResponse create(Long userId, String boardUrl, ScheduledPostRequest request) {
        User user = userWritableResolver.resolve(userId);
        sanctionService.validateNotMuted(user);
        String normalizedBoardUrl = BoardUrlNormalizer.normalizeLookup(boardUrl);
        Board board = boardRepository.findByBoardUrl(normalizedBoardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        postAuthorCommandPolicy.validateBoardWritable(board, user);
        validateRequest(request);

        ScheduledPost scheduledPost = ScheduledPost.builder()
                .user(user)
                .board(board)
                .categoryId(request.getCategoryId())
                .title(request.getTitle())
                .contents(request.getContents())
                .isNotice(request.isNotice())
                .isNsfw(request.isNsfw())
                .isSpoiler(request.isSpoiler())
                .isSecret(request.isSecret())
                .tagsJson(payloadMapper.tagsJson(request))
                .fileIdsJson(payloadMapper.fileIdsJson(request))
                .pollJson(payloadMapper.pollJson(request))
                .seriesId(request.getSeriesId())
                .scheduledAt(request.getScheduledAt())
                .build();
        return ScheduledPostResponse.from(scheduledPostRepository.save(scheduledPost));
    }

    public Page<ScheduledPostResponse> getMine(Long userId, Pageable pageable) {
        userWritableResolver.resolve(userId);
        Pageable safePageable = PageRequestUtils.of(pageable, DEFAULT_PAGE_SIZE, DEFAULT_SORT);
        return scheduledPostRepository.findByUser_UserIdOrderByScheduledAtDescScheduledPostIdDesc(userId, safePageable)
                .map(ScheduledPostResponse::from);
    }

    public ScheduledPostResponse getOwned(Long userId, Long scheduledPostId) {
        return ScheduledPostResponse.from(loadOwned(userId, scheduledPostId));
    }

    @Transactional
    public ScheduledPostResponse update(Long userId, Long scheduledPostId, ScheduledPostRequest request) {
        User user = userWritableResolver.resolve(userId);
        sanctionService.validateNotMuted(user);
        ScheduledPost scheduledPost = loadOwned(userId, scheduledPostId);
        if (!scheduledPost.isEditable()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        postAuthorCommandPolicy.validateBoardWritable(scheduledPost.getBoard(), user);
        validateRequest(request);
        scheduledPost.update(
                request.getCategoryId(),
                request.getTitle(),
                request.getContents(),
                request.isNotice(),
                request.isNsfw(),
                request.isSpoiler(),
                request.isSecret(),
                payloadMapper.tagsJson(request),
                payloadMapper.fileIdsJson(request),
                payloadMapper.pollJson(request),
                request.getSeriesId(),
                request.getScheduledAt());
        return ScheduledPostResponse.from(scheduledPost);
    }

    @Transactional
    public void cancel(Long userId, Long scheduledPostId) {
        userWritableResolver.resolve(userId);
        int updated = scheduledPostRepository.cancelOwned(scheduledPostId, userId, now());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    @Transactional
    public int publishDuePosts() {
        LocalDateTime now = now();
        List<Long> dueIds = scheduledPostRepository
                .findDueScheduledPostIds(now, org.springframework.data.domain.PageRequest.of(0, PUBLISH_BATCH_SIZE))
                .stream()
                .map(ScheduledPostRepository.ScheduledPostIdProjection::getScheduledPostId)
                .toList();
        int publishedCount = 0;
        for (Long scheduledPostId : dueIds) {
            if (claimAndPublish(scheduledPostId, now)) {
                publishedCount++;
            }
        }
        return publishedCount;
    }

    private boolean claimAndPublish(Long scheduledPostId, LocalDateTime now) {
        LocalDateTime claimedAt = now();
        int claimed = scheduledPostRepository.claimForPublishing(scheduledPostId, now, claimedAt);
        if (claimed != 1) {
            return false;
        }
        publishClaimedPost(scheduledPostId, claimedAt);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void publishClaimedPost(Long scheduledPostId, LocalDateTime claimedAt) {
        ScheduledPost scheduledPost = scheduledPostRepository
                .findByScheduledPostIdAndStatusAndProcessingStartedAt(
                        scheduledPostId,
                        ScheduledPost.STATUS_PUBLISHING,
                        claimedAt)
                .orElse(null);
        if (scheduledPost == null) {
            return;
        }
        try {
            PostCreateResponse created = postCommandService.createPostWithResponse(
                    scheduledPost.getUser().getUserId(),
                    scheduledPost.getBoard().getBoardUrl(),
                    payloadMapper.toPostCreateRequest(scheduledPost));
            scheduledPostRepository.markPublished(scheduledPostId, claimedAt, created.getPostId(), now());
            publishSystemNotification(
                    scheduledPost.getUser(),
                    scheduledPostId,
                    "Scheduled post published: " + scheduledPost.getTitle());
        } catch (RuntimeException exception) {
            String reason = normalizeFailureReason(exception);
            log.warn("Scheduled post publish failed. scheduledPostId={}, reason={}", scheduledPostId, reason);
            scheduledPostRepository.markFailed(scheduledPostId, claimedAt, reason);
            publishSystemNotification(
                    scheduledPost.getUser(),
                    scheduledPostId,
                    "Scheduled post failed: " + scheduledPost.getTitle());
        }
    }

    private ScheduledPost loadOwned(Long userId, Long scheduledPostId) {
        return scheduledPostRepository.findByScheduledPostIdAndUser_UserId(scheduledPostId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void validateRequest(ScheduledPostRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        PostTitleValidator.validate(request.getTitle());
        if (request.getContents() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        validateScheduledAt(request.getScheduledAt());
    }

    private void validateScheduledAt(LocalDateTime scheduledAt) {
        LocalDateTime current = now();
        if (scheduledAt == null
                || scheduledAt.isBefore(current.plusMinutes(MIN_SCHEDULE_MINUTES))
                || scheduledAt.isAfter(current.plusDays(MAX_SCHEDULE_DAYS))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
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
        if (normalized.length() <= MAX_FAILURE_REASON_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_FAILURE_REASON_LENGTH);
    }

    private void publishSystemNotification(User user, Long scheduledPostId, String content) {
        eventPublisher.publishEvent(new NotificationEvent(
                user,
                null,
                NotificationType.SYSTEM,
                NotificationSourceType.SYSTEM,
                scheduledPostId,
                content));
    }
}
