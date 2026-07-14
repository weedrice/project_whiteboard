package com.weedrice.whiteboard.domain.post.scheduled.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.util.BoardUrlNormalizer;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.repository.DraftPostRepository;
import com.weedrice.whiteboard.domain.post.scheduled.dto.ScheduledPostRequest;
import com.weedrice.whiteboard.domain.post.scheduled.dto.ScheduledPostResponse;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import com.weedrice.whiteboard.domain.post.scheduled.repository.ScheduledPostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAuthorCommandPolicy;
import com.weedrice.whiteboard.domain.post.service.PostTitleValidator;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("scheduledAt"),
            Sort.Order.desc("scheduledPostId"));

    private final ScheduledPostRepository scheduledPostRepository;
    private final DraftPostRepository draftPostRepository;
    private final BoardRepository boardRepository;
    private final UserWritableResolver userWritableResolver;
    private final SanctionService sanctionService;
    private final PostAuthorCommandPolicy postAuthorCommandPolicy;
    private final ScheduledPostPayloadMapper payloadMapper;
    private final ScheduledPostPublishWorker publishWorker;
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
        validateDraftReference(request.getDraftId(), user, board);
        validateDraftAvailability(request.getDraftId(), null);

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
                .draftId(request.getDraftId())
                .scheduledAt(request.getScheduledAt())
                .build();
        try {
            return ScheduledPostResponse.from(scheduledPostRepository.saveAndFlush(scheduledPost));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
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
        validateDraftReference(request.getDraftId(), user, scheduledPost.getBoard());
        validateDraftAvailability(request.getDraftId(), scheduledPostId);
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
                request.getDraftId(),
                request.getScheduledAt());
        try {
            scheduledPostRepository.flush();
            return ScheduledPostResponse.from(scheduledPost);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    @Transactional
    public void cancel(Long userId, Long scheduledPostId) {
        userWritableResolver.resolve(userId);
        int updated = scheduledPostRepository.cancelOwned(scheduledPostId, userId, now());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int publishDuePosts() {
        LocalDateTime now = now();
        List<Long> dueIds = scheduledPostRepository
                .findDueScheduledPostIds(now, org.springframework.data.domain.PageRequest.of(0, PUBLISH_BATCH_SIZE))
                .stream()
                .map(ScheduledPostRepository.ScheduledPostIdProjection::getScheduledPostId)
                .toList();
        int publishedCount = 0;
        for (Long scheduledPostId : dueIds) {
            LocalDateTime claimedAt = now();
            if (!publishWorker.claim(scheduledPostId, now, claimedAt)) {
                continue;
            }
            try {
                publishWorker.publishClaimed(scheduledPostId, claimedAt);
                publishedCount++;
            } catch (RuntimeException exception) {
                publishWorker.markFailed(scheduledPostId, claimedAt, exception);
            }
        }
        return publishedCount;
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

    private void validateDraftReference(Long draftId, User user, Board board) {
        if (draftId == null) {
            return;
        }
        DraftPost draft = draftPostRepository.findByDraftIdAndUserForUpdate(draftId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!draft.getBoard().getBoardId().equals(board.getBoardId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateDraftAvailability(Long draftId, Long scheduledPostId) {
        if (draftId == null) {
            return;
        }
        boolean alreadyScheduled = scheduledPostId == null
                ? scheduledPostRepository.existsByDraftId(draftId)
                : scheduledPostRepository.existsByDraftIdAndScheduledPostIdNot(draftId, scheduledPostId);
        if (alreadyScheduled) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private LocalDateTime now() {
        LocalDateTime now = LocalDateTime.now(clock);
        return now.withNano((now.getNano() / 1_000) * 1_000);
    }

}
