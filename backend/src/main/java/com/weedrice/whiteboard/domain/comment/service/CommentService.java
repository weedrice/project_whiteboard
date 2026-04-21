package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentOwnershipService;
import com.weedrice.whiteboard.domain.comment.dto.CommentListResponse;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.dto.MyCommentResponse;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.entity.CommentLike;
import com.weedrice.whiteboard.domain.comment.entity.CommentLikeId;
import com.weedrice.whiteboard.domain.comment.entity.CommentVersion;
import com.weedrice.whiteboard.domain.comment.repository.CommentClosureRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentLikeRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentVersionRepository;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.point.entity.PointHistory;
import com.weedrice.whiteboard.domain.point.repository.PointHistoryRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final CommentVersionRepository commentVersionRepository;
    private final CommentClosureRepository commentClosureRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PointService pointService;
    private final PointHistoryRepository pointHistoryRepository;
    private final UserBlockService userBlockService;
    private final GlobalConfigService globalConfigService;
    private final AgentOwnershipService agentOwnershipService;
    private final SanctionService sanctionService;
    private final PostAccessPolicy postAccessPolicy;

    // Contract: /posts/{postId}/comments pages only parent comments; replies are fetched lazily via /comments/{id}/replies.
    public Page<CommentResponse> getComments(Long postId, Long currentUserId, Pageable pageable) {
        Objects.requireNonNull(pageable, "Pageable must not be null");
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        validatePostReadable(post, currentUserId);

        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        }

        final List<Long> finalBlockedUserIds = blockedUserIds;

        Page<Comment> parentComments = commentRepository.findParentsWithChildrenOrNotDeleted(postId, pageable);
        if (parentComments.isEmpty()) {
            List<CommentResponse> emptyContent = new java.util.ArrayList<>();
            return new PageImpl<>(emptyContent, pageable, parentComments.getTotalElements());
        }

        Map<Long, Long> replyCounts = loadVisibleReplyCounts(parentComments.getContent());
        List<CommentResponse> responseContent = parentComments.getContent().stream()
                .map(comment -> toMaskedCommentResponse(comment, finalBlockedUserIds, replyCounts))
                .collect(Collectors.toList());

        return new PageImpl<>(responseContent, pageable, parentComments.getTotalElements());
    }

    public CommentListResponse getReplies(Long parentId, Long currentUserId, Pageable pageable) {
        Comment parentComment = commentRepository.findNonDeletedByIdWithRelations(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        validatePostReadable(parentComment.getPost(), currentUserId);

        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        }
        final List<Long> finalBlockedUserIds = blockedUserIds;

        Page<Comment> replies = commentRepository.findRepliesWithRelations(parentId, false, pageable);
        Map<Long, Long> replyCounts = loadVisibleReplyCounts(replies.getContent());
        List<CommentResponse> maskedReplies = replies.getContent().stream()
                .map(comment -> toMaskedCommentResponse(comment, finalBlockedUserIds, replyCounts))
                .collect(Collectors.toList());

        return CommentListResponse.builder()
                .content(maskedReplies)
                .page(replies.getNumber())
                .size(replies.getSize())
                .totalElements(replies.getTotalElements())
                .totalPages(replies.getTotalPages())
                .hasNext(replies.hasNext())
                .hasPrevious(replies.hasPrevious())
                .build();
    }

    public CommentResponse getComment(Long commentId, Long currentUserId) {
        Comment comment = commentRepository.findNonDeletedByIdWithRelations(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        validatePostReadable(comment.getPost(), currentUserId);
        List<Long> blockedUserIds = currentUserId == null ? null : userBlockService.getBlockedUserIds(currentUserId);
        return toMaskedCommentResponse(comment, blockedUserIds);
    }

    public Page<MyCommentResponse> getMyComments(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return commentRepository.findByUserAndIsDeletedOrderByCreatedAtDesc(user, false, pageable)
                .map(MyCommentResponse::from);
    }

    @Transactional
    public Comment createComment(Long userId, Long postId, Long parentId, String content) {
        return createComment(userId, null, postId, parentId, content);
    }

    @Transactional
    public Comment createCommentAsAgent(Long userId, Long agentId, Long postId, Long parentId, String content) {
        return createComment(userId, agentId, postId, parentId, content);
    }

    @Transactional
    public Comment createComment(Long userId, Long agentId, Long postId, Long parentId, String content) {
        User user = getWritableUser(userId);
        Agent agent = agentOwnershipService.resolveOwnedActiveAgent(userId, agentId);
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        validatePostReadable(post, user);

        Comment parentComment = null;
        int depth = 0;
        if (parentId != null) {
            parentComment = commentRepository.findById(parentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

            if (parentComment.getIsDeleted()) {
                throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
            }
            if (!Objects.equals(parentComment.getPost().getPostId(), post.getPostId())) {
                throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
            }

            depth = parentComment.getDepth() + 1;
        }

        // ?볤? ?댁슜?먯꽌 HTML ?쒓렇瑜??쒓굅?쒕떎.
        String sanitizedContent = InputSanitizer.stripHtml(content);

        Comment comment = Comment.builder()
                .post(post)
                .user(user)
                .agent(agent)
                .parent(parentComment)
                .depth(depth)
                .content(sanitizedContent)
                .build();

        Comment savedComment = commentRepository.save(comment);
        postRepository.incrementCommentCount(post.getPostId());

        saveCommentVersion(savedComment, user, "CREATE", null);

        if (parentId != null) {
            commentClosureRepository.createClosures(savedComment.getCommentId(), parentId);
        } else {
            commentClosureRepository.createSelfClosure(savedComment.getCommentId());
        }

        String commentCreateRewardStr = globalConfigService.getConfig("POINT_COMMENT_CREATE_REWARD");
        int commentCreateReward = commentCreateRewardStr != null ? Integer.parseInt(commentCreateRewardStr) : 10;
        pointService.addPoint(userId, commentCreateReward, "\uB313\uAE00 \uC791\uC131", savedComment.getCommentId(), "COMMENT");

        if (parentComment != null && parentComment.getAgent() == null) {
            String notificationContent = resolveNotificationActorName(user, agent)
                    + "\uB2D8\uC774 \uD68C\uC6D0\uB2D8\uC758 \uB313\uAE00\uC5D0 \uB2F5\uAE00\uC744 \uB0A8\uACBC\uC2B5\uB2C8\uB2E4.";
            NotificationEvent event = new NotificationEvent(parentComment.getUser(), user, agent,
                    NotificationType.REPLY, "COMMENT", parentId, notificationContent);
            eventPublisher.publishEvent(event);
        } else if (parentComment == null && post.getAgent() == null) {
            String notificationContent = resolveNotificationActorName(user, agent)
                    + "\uB2D8\uC774 \uD68C\uC6D0\uB2D8\uC758 \uAC8C\uC2DC\uAE00\uC5D0 \uB313\uAE00\uC744 \uB0A8\uACBC\uC2B5\uB2C8\uB2E4.";
            NotificationEvent event = new NotificationEvent(post.getUser(), user, agent,
                    NotificationType.COMMENT, "POST", postId, notificationContent);
            eventPublisher.publishEvent(event);
        }

        return savedComment;
    }

    @Transactional
    public Comment updateComment(Long userId, Long commentId, String content) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        User user = getWritableUser(userId);
        validatePostReadable(comment.getPost(), user);

        if (comment.getIsDeleted()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        if (!comment.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        String originalContent = comment.getContent();
        // ?볤? ?댁슜?먯꽌 HTML ?쒓렇瑜??쒓굅?쒕떎.
        String sanitizedContent = InputSanitizer.stripHtml(content);
        comment.updateContent(sanitizedContent);

        saveCommentVersion(comment, user, "MODIFY", originalContent);
        return comment;
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        User user = getWritableUser(userId);
        validatePostReadable(comment.getPost(), user);

        if (comment.getIsDeleted()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        if (!comment.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        String originalContent = comment.getContent();
        comment.deleteComment();
        postRepository.decrementCommentCount(comment.getPost().getPostId());

        saveCommentVersion(comment, user, "DELETE", originalContent);

        int rewardedAmount = getCommentCreateRewardAmount(user, commentId);
        if (rewardedAmount > 0) {
            pointService.forceSubtractPoint(userId, rewardedAmount, "\uB313\uAE00 \uC0AD\uC81C", commentId, "COMMENT");
        }
    }

    @Transactional
    public void likeComment(Long userId, Long commentId) {
        User user = getWritableUser(userId);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        validatePostReadable(comment.getPost(), user);

        if (comment.getIsDeleted()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        boolean skipNotification = comment.getAgent() != null;
        User commentOwner = comment.getUser();

        CommentLike commentLike = CommentLike.builder()
                .user(user)
                .comment(comment)
                .build();
        try {
            commentLikeRepository.saveAndFlush(commentLike);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.ALREADY_LIKED);
        }
        commentRepository.incrementLikeCount(commentId);
        if (skipNotification) {
            return;
        }

        String content = resolveNotificationActorName(user, null)
                + "\uB2D8\uC774 \uD68C\uC6D0\uB2D8\uC758 \uB313\uAE00\uC744 \uC88B\uC544\uD569\uB2C8\uB2E4.";
        NotificationEvent event = new NotificationEvent(commentOwner, user, NotificationType.LIKE,
                "COMMENT", commentId, content);
        eventPublisher.publishEvent(event);
    }

    @Transactional
    public void unlikeComment(Long userId, Long commentId) {
        User user = getWritableUser(userId);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        validatePostReadable(comment.getPost(), user);

        int deletedCount = commentLikeRepository.deleteByUserIdAndCommentId(userId, commentId);
        if (deletedCount == 0) {
            throw new BusinessException(ErrorCode.NOT_LIKED);
        }

        commentRepository.decrementLikeCount(commentId);
    }

    private void saveCommentVersion(Comment comment, User modifier, String versionType, String originalContent) {
        CommentVersion commentVersion = CommentVersion.builder()
                .comment(comment)
                .modifier(modifier)
                .versionType(versionType)
                .originalContent(originalContent)
                .build();
        commentVersionRepository.save(commentVersion);
    }

    private int getCommentCreateRewardAmount(User user, Long commentId) {
        return pointHistoryRepository.findByUserAndTypeAndRelatedTypeAndRelatedIdOrderByCreatedAtAsc(
                        user,
                        "EARN",
                        "COMMENT",
                        commentId)
                .stream()
                .map(PointHistory::getAmount)
                .filter(amount -> amount != null && amount > 0)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private User getWritableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        sanctionService.validateNotBanned(user);
        return user;
    }

    private String resolveNotificationActorName(User user, Agent agent) {
        if (agent != null && agent.getName() != null && !agent.getName().isBlank()) {
            return agent.getName();
        }
        return user.getDisplayName();
    }

    private CommentResponse maskCommentContent(CommentResponse response, List<Long> blockedUserIds) {
        if (blockedUserIds != null && response.getAuthor() != null
                && blockedUserIds.contains(response.getAuthor().getUserId())) {
            return response.toBuilder()
                    .content("\uCC28\uB2E8\uB41C \uC0AC\uC6A9\uC790\uC758 \uB313\uAE00\uC785\uB2C8\uB2E4.")
                    .author(CommentResponse.AuthorInfo.builder()
                            .userId(response.getAuthor().getUserId())
                            .displayName("\uCC28\uB2E8\uB41C \uC0AC\uC6A9\uC790")
                            .profileImageUrl(null)
                            .build())
                    .build();
        }
        return response;
    }

    private CommentResponse toMaskedCommentResponse(Comment comment, List<Long> blockedUserIds) {
        return toMaskedCommentResponse(comment, blockedUserIds, java.util.Collections.emptyMap());
    }

    private CommentResponse toMaskedCommentResponse(Comment comment, List<Long> blockedUserIds, Map<Long, Long> replyCounts) {
        long replyCount = replyCounts.getOrDefault(comment.getCommentId(), 0L);
        CommentResponse response = CommentResponse.from(comment).toBuilder()
                .replyCount(replyCount)
                .hasReplies(replyCount > 0)
                .build();
        return maskCommentContent(response, blockedUserIds);
    }

    private Map<Long, Long> loadVisibleReplyCounts(List<Comment> comments) {
        List<Long> commentIds = comments.stream()
                .map(Comment::getCommentId)
                .collect(Collectors.toList());
        if (commentIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        List<CommentRepository.ReplyCountProjection> replyCounts = commentRepository.countVisibleRepliesByParentIds(commentIds);
        if (replyCounts == null || replyCounts.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        return replyCounts.stream()
                .collect(Collectors.toMap(
                        CommentRepository.ReplyCountProjection::getParentId,
                        CommentRepository.ReplyCountProjection::getReplyCount,
                        (left, right) -> right));
    }

    private void validatePostReadable(Post post, Long currentUserId) {
        User viewer = currentUserId == null ? null : userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validatePostReadable(post, viewer);
    }

    private void validatePostReadable(Post post, User viewer) {
        List<Long> blockedUserIds = viewer == null ? null : userBlockService.getBlockedUserIds(viewer.getUserId());
        boolean authorBlocked = viewer != null
                && blockedUserIds != null
                && blockedUserIds.contains(post.getUser().getUserId());
        postAccessPolicy.validateReadable(post, viewer, authorBlocked);
    }
}


