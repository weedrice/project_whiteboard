package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.moderation.service.ModerationAuditLogService;
import com.weedrice.whiteboard.domain.notification.service.NotificationAccessInvalidationService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchEventPublisher;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchIndexAction;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class PostManagerModerationService {

    private static final String DEFAULT_MANAGER_BLIND_REASON = "MANAGER";

    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final BoardAccessPolicy boardAccessPolicy;
    private final ModerationAuditLogService moderationAuditLogService;
    private final SemanticSearchEventPublisher semanticSearchEventPublisher;
    private final NotificationAccessInvalidationService notificationAccessInvalidationService;
    private final Clock clock;

    public void pinPost(Long managerUserId, Long postId) {
        ManagedPost managedPost = loadManagedPost(managerUserId, postId);
        Post post = managedPost.post();
        post.pin(now());
        recordPostAction(managedPost.manager(), post, ModerationAuditLogService.ACTION_POST_PIN, null);
    }

    public void unpinPost(Long managerUserId, Long postId) {
        ManagedPost managedPost = loadManagedPost(managerUserId, postId);
        Post post = managedPost.post();
        post.unpin();
        recordPostAction(managedPost.manager(), post, ModerationAuditLogService.ACTION_POST_UNPIN, null);
    }

    public void blindPost(Long managerUserId, Long postId, String reason) {
        ManagedPost managedPost = loadManagedPost(managerUserId, postId);
        Post post = managedPost.post();
        String normalizedReason = normalizeReason(reason);
        post.blind(normalizedReason, now());
        notificationAccessInvalidationService.invalidateCommentTopicAfterCommit(post.getPostId());
        semanticSearchEventPublisher.publish("POST", post.getPostId(), SemanticSearchIndexAction.DELETE);
        recordPostAction(managedPost.manager(), post, ModerationAuditLogService.ACTION_POST_BLIND, normalizedReason);
    }

    public void unblindPost(Long managerUserId, Long postId) {
        ManagedPost managedPost = loadManagedPost(managerUserId, postId);
        Post post = managedPost.post();
        post.unblind();
        semanticSearchEventPublisher.publish("POST", post.getPostId(), SemanticSearchIndexAction.UPSERT);
        semanticSearchEventPublisher.publishPostCommentsReindex(post.getPostId());
        recordPostAction(managedPost.manager(), post, ModerationAuditLogService.ACTION_POST_UNBLIND, null);
    }

    private ManagedPost loadManagedPost(Long managerUserId, Long postId) {
        User manager = userRepository.findByIdForUpdate(managerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Long boardId = postRepository.findBoardIdByPostId(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        Board board = boardRepository.findByIdForUpdate(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        Post post = postRepository.findByIdWithRelationsForBlindUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        if (!boardId.equals(post.getBoard().getBoardId())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        boardAccessPolicy.validateBoardAdmin(board, manager);
        return new ManagedPost(manager, post);
    }

    private void recordPostAction(User manager, Post post, String action, String reason) {
        moderationAuditLogService.recordUserAction(
                manager,
                action,
                ModerationAuditLogService.TARGET_TYPE_POST,
                post.getPostId(),
                post.getBoard(),
                reason);
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return DEFAULT_MANAGER_BLIND_REASON;
        }
        return reason.trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private record ManagedPost(User manager, Post post) {
    }
}
