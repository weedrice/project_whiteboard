package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.dto.ViewHistoryRequest;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.repository.ViewHistoryRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final ViewHistoryCommandService viewHistoryCommandService;
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final PostSummaryAssembler postSummaryAssembler;

    @Transactional
    public void touchView(User user, Post post) {
        viewHistoryCommandService.touchView(user, post);
    }

    public ViewHistory get(User user, Post post) {
        return viewHistoryRepository.findByUserAndPost(user, post).orElse(null);
    }

    @Transactional
    public void update(User user, Post post, ViewHistoryRequest request) {
        long durationMs = resolveDurationMs(request);
        Comment lastReadComment = resolveLastReadComment(post.getPostId(), request.getLastReadCommentId());
        ViewHistory viewHistory = viewHistoryCommandService.getOrCreateForUpdate(user, post);
        validateDurationAccumulation(viewHistory.getDurationMs(), durationMs);
        viewHistory.updateView(lastReadComment, durationMs);
    }

    public Page<PostSummary> getRecentlyViewedPosts(
            Long userId,
            PostReadContext context,
            Pageable pageable) {
        User user = context.viewer();
        Pageable safePageable = PageRequestUtils.of(pageable, DEFAULT_PAGE_SIZE, DEFAULT_SORT);
        BlockedUserFilter blockedUsers = BlockedUserFilter.from(context.blockedUserIdSet());
        Page<Long> visiblePostIdsPage = viewHistoryRepository.findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                userId,
                user.isUsableSuperAdmin(),
                blockedUsers.empty(),
                blockedUsers.ids(),
                BoardPolicyConstants.INQUIRY_BOARD_URL,
                safePageable);

        if (visiblePostIdsPage.isEmpty()) {
            return Page.empty(safePageable);
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

    private Comment resolveLastReadComment(Long postId, Long lastReadCommentId) {
        if (lastReadCommentId == null) {
            return null;
        }
        return commentRepository.findByCommentIdAndPost_PostIdAndIsDeletedFalse(lastReadCommentId, postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));
    }

    private record BlockedUserFilter(boolean empty, List<Long> ids) {
        private static final List<Long> NO_BLOCKED_USER_IDS = List.of(-1L);

        static BlockedUserFilter from(Set<Long> blockedUserIds) {
            if (blockedUserIds == null || blockedUserIds.isEmpty()) {
                return new BlockedUserFilter(true, NO_BLOCKED_USER_IDS);
            }
            return new BlockedUserFilter(false, new ArrayList<>(blockedUserIds));
        }
    }
}
