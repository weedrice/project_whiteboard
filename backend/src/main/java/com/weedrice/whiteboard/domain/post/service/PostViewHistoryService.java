package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.inquiry.legacy.InquiryLegacyWritePolicy;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.dto.ViewHistoryRequest;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.post.entity.ViewHistorySession;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.repository.ViewHistoryRepository;
import com.weedrice.whiteboard.domain.post.repository.ViewHistorySessionRepository;
import com.weedrice.whiteboard.domain.post.port.PostCommentStatusPort;
import com.weedrice.whiteboard.domain.actor.ActorUserPrincipal;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostViewHistoryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    // Native query pageable sorting is appended as SQL, so use column names here.
    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("modified_at"),
            Sort.Order.desc("post_id"));

    private final ViewHistoryRepository viewHistoryRepository;
    private final ViewHistorySessionRepository sessionRepository;
    private final ViewHistoryCommandService viewHistoryCommandService;
    private final PostCommentStatusPort postCommentStatusPort;
    private final PostRepository postRepository;
    private final PostSummaryAssembler postSummaryAssembler;
    private final InquiryLegacyWritePolicy inquiryLegacyWritePolicy;

    @Transactional
    public void touchView(ActorUserPrincipal user, Post post) {
        viewHistoryCommandService.touchView(user, post);
    }

    public ViewHistory get(ActorUserPrincipal user, Post post) {
        return viewHistoryRepository.findByUserIdAndPost(user.getUserId(), post).orElse(null);
    }

    @Transactional
    public void update(ActorUserPrincipal user, Post post, ViewHistoryRequest request) {
        long durationMs = resolveDurationMs(request);
        var existing = viewHistoryRepository.findByUserAndPostForUpdate(user.getUserId(), post.getPostId());
        Long lastReadCommentId = request.getLastReadCommentId();
        Long storedCommentId = existing.map(ViewHistory::getLastReadCommentId).orElse(null);
        if (lastReadCommentId != null && !lastReadCommentId.equals(storedCommentId)) {
            if (storedCommentId != null && lastReadCommentId < storedCommentId) {
                postCommentStatusPort.requireCommentOnPost(post.getPostId(), lastReadCommentId);
            } else {
                postCommentStatusPort.requireActiveCommentId(post.getPostId(), lastReadCommentId);
            }
        }
        ViewHistory viewHistory = existing.orElseGet(() -> viewHistoryCommandService.insertAndLoadForUpdate(user, post));
        if (request.getViewSessionId() != null) {
            durationMs = applySessionDuration(viewHistory, request, durationMs);
        }
        validateDurationAccumulation(viewHistory.getDurationMs(), durationMs);
        viewHistory.updateView(lastReadCommentId, durationMs);
    }

    public Page<PostSummary> getRecentlyViewedPosts(
            Long userId,
            PostReadContext context,
            Pageable pageable) {
        ActorUserPrincipal user = context.viewer();
        Pageable safePageable = PageRequestUtils.of(pageable, DEFAULT_PAGE_SIZE, DEFAULT_SORT);
        BlockedUserFilter blockedUsers = BlockedUserFilter.from(context.blockedUserIdSet());
        Page<Long> visiblePostIdsPage = viewHistoryRepository.findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                userId,
                user.isUsableSuperAdmin(),
                blockedUsers.empty(),
                blockedUsers.ids(),
                BoardPolicyConstants.INQUIRY_BOARD_URL,
                inquiryLegacyWritePolicy.areLegacyWritesEnabled(),
                safePageable);

        if (visiblePostIdsPage.isEmpty()) {
            return new PageImpl<>(List.of(), safePageable, visiblePostIdsPage.getTotalElements());
        }

        Map<Long, Post> postsById = postRepository
                .findByPostIdInAndIsDeletedFalseAndIsBlindedFalse(visiblePostIdsPage.getContent())
                .stream()
                .collect(Collectors.toMap(Post::getPostId, post -> post));
        List<Post> orderedPosts = visiblePostIdsPage.getContent().stream()
                .map(postsById::get)
                .filter(Objects::nonNull)
                .toList();
        List<PostSummary> orderedSummaries = postSummaryAssembler.assembleLatestPosts(orderedPosts, userId);
        return new PageImpl<>(orderedSummaries, safePageable, visiblePostIdsPage.getTotalElements());
    }

    private long resolveDurationMs(ViewHistoryRequest request) {
        Long durationMs = request.getDurationMs();
        Long cumulative = request.getSessionDurationMs();
        if ((cumulative == null) != (request.getViewSessionId() == null)
                || cumulative != null && (durationMs == null || cumulative < 0)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (durationMs == null) {
            return 0L;
        }
        if (durationMs < 0 || durationMs > ViewHistoryRequest.MAX_DURATION_MS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return durationMs;
    }

    private void validateDurationAccumulation(long currentDurationMs, long durationMs) {
        if (durationMs > 0 && currentDurationMs > Long.MAX_VALUE - durationMs) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // All sessions for this user and post share the locked parent history.
    private long applySessionDuration(ViewHistory history, ViewHistoryRequest request, long unconfirmedDurationMs) {
        long reported = request.getSessionDurationMs();
        var id = new ViewHistorySession.Id(history.getViewHistoryId(), request.getViewSessionId());
        var existing = sessionRepository.findById(id);
        long recorded = existing.map(ViewHistorySession::getDurationMs).orElse(0L);
        if (reported <= recorded) {
            return 0;
        }
        // A previous server may already have acknowledged older deltas without storing a session.
        // Advance to the full watermark but never credit more than this request's unconfirmed duration.
        long delta = Math.min(reported - recorded, unconfirmedDurationMs);
        var session = existing.orElseGet(() -> new ViewHistorySession(history, request.getViewSessionId()));
        session.recordDuration(reported);
        if (existing.isEmpty()) {
            sessionRepository.save(session);
        }
        return delta;
    }
}
