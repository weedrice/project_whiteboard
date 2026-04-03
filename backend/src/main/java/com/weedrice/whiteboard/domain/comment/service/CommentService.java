package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentOwnershipService;
import com.weedrice.whiteboard.domain.board.entity.Board;
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
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private static final String DEFAULT_INQUIRY_BOARD_URL = "inquiry";

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final CommentVersionRepository commentVersionRepository;
    private final CommentClosureRepository commentClosureRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PointService pointService;
    private final UserBlockService userBlockService;
    private final GlobalConfigService globalConfigService;
    private final AdminRepository adminRepository;
    private final AgentOwnershipService agentOwnershipService;

    public Page<CommentResponse> getComments(Long postId, Long currentUserId, Pageable pageable) {
        Objects.requireNonNull(pageable, "Pageable must not be null");
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (post.getIsDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        validateInquiryCommentReadable(post, currentUserId);

        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        }

        final List<Long> finalBlockedUserIds = blockedUserIds;

        Page<Comment> parentComments = commentRepository.findParentsWithChildrenOrNotDeleted(postId, pageable);
        List<Long> parentIds = parentComments.getContent().stream()
                .map(Comment::getCommentId)
                .collect(Collectors.toList());

        if (parentIds.isEmpty()) {
            List<CommentResponse> emptyContent = new java.util.ArrayList<>();
            return new PageImpl<>(emptyContent, pageable, parentComments.getTotalElements());
        }

        List<Comment> allDescendants = commentRepository.findAllDescendants(parentIds);

        Map<Long, CommentResponse> responseMap = new java.util.HashMap<>();

        parentComments.getContent().forEach(c -> responseMap.put(
                c.getCommentId(),
                maskCommentContent(CommentResponse.from(c), finalBlockedUserIds)));

        allDescendants.forEach(c -> responseMap.put(
                c.getCommentId(),
                maskCommentContent(CommentResponse.from(c), finalBlockedUserIds)));

        allDescendants.forEach(child -> {
            CommentResponse childResponse = responseMap.get(child.getCommentId());
            if (child.getParent() != null) {
                Long parentId = child.getParent().getCommentId();
                if (responseMap.containsKey(parentId)) {
                    responseMap.get(parentId).getChildren().add(childResponse);
                }
            }
        });

        List<CommentResponse> responseContent = new java.util.ArrayList<>(
                parentComments.getContent().stream()
                        .map(c -> responseMap.get(c.getCommentId()))
                        .collect(Collectors.toList()));

        return new PageImpl<>(responseContent, pageable, parentComments.getTotalElements());
    }

    public CommentListResponse getReplies(Long parentId, Long currentUserId, Pageable pageable) {
        Comment parentComment = commentRepository.findByIdWithRelations(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        validateInquiryCommentReadable(parentComment.getPost(), currentUserId);
        Page<Comment> replies = commentRepository.findByParent_CommentIdAndIsDeletedOrderByCreatedAtAsc(parentId, false,
                pageable);
        return CommentListResponse.from(replies);
    }

    public CommentResponse getComment(Long commentId, Long currentUserId) {
        Comment comment = commentRepository.findByIdWithRelations(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        validateInquiryCommentReadable(comment.getPost(), currentUserId);
        return CommentResponse.from(comment);
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Agent agent = agentOwnershipService.resolveOwnedActiveAgent(userId, agentId);
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (post.getIsDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        validateInquiryCommentReadableByUser(post, user);

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

        post.incrementCommentCount();
        Comment savedComment = commentRepository.save(comment);

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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validateInquiryCommentReadableByUser(comment.getPost(), user);

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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validateInquiryCommentReadableByUser(comment.getPost(), user);

        if (comment.getIsDeleted()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        if (!comment.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        String originalContent = comment.getContent();
        comment.deleteComment();
        comment.getPost().decrementCommentCount();

        saveCommentVersion(comment, user, "DELETE", originalContent);

        String commentCreateRewardStr = globalConfigService.getConfig("POINT_COMMENT_CREATE_REWARD");
        int commentCreateReward = commentCreateRewardStr != null ? Integer.parseInt(commentCreateRewardStr) : 10;
        pointService.forceSubtractPoint(userId, commentCreateReward, "\uB313\uAE00 \uC0AD\uC81C", commentId, "COMMENT");
    }

    @Transactional
    public void likeComment(Long userId, Long commentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        validateInquiryCommentReadableByUser(comment.getPost(), user);

        if (comment.getIsDeleted()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        CommentLikeId commentLikeId = new CommentLikeId(userId, commentId);
        if (commentLikeRepository.existsById(commentLikeId)) {
            throw new BusinessException(ErrorCode.ALREADY_LIKED);
        }

        CommentLike commentLike = CommentLike.builder()
                .user(user)
                .comment(comment)
                .build();
        commentLikeRepository.save(commentLike);
        comment.incrementLikeCount();
        if (comment.getAgent() != null) {
            return;
        }

        String content = resolveNotificationActorName(user, null)
                + "\uB2D8\uC774 \uD68C\uC6D0\uB2D8\uC758 \uB313\uAE00\uC744 \uC88B\uC544\uD569\uB2C8\uB2E4.";
        NotificationEvent event = new NotificationEvent(comment.getUser(), user, NotificationType.LIKE,
                "COMMENT", commentId, content);
        eventPublisher.publishEvent(event);
    }

    @Transactional
    public void unlikeComment(Long userId, Long commentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        validateInquiryCommentReadableByUser(comment.getPost(), user);

        CommentLikeId commentLikeId = new CommentLikeId(userId, commentId);
        if (!commentLikeRepository.existsById(commentLikeId)) {
            throw new BusinessException(ErrorCode.NOT_LIKED);
        }

        commentLikeRepository.deleteById(commentLikeId);
        comment.decrementLikeCount();
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

    private void validateInquiryCommentReadable(Post post, Long currentUserId) {
        if (!isInquiryBoard(post)) {
            return;
        }
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validateInquiryCommentReadableByUser(post, user);
    }

    private void validateInquiryCommentReadableByUser(Post post, User user) {
        if (!isInquiryBoard(post)) {
            return;
        }
        if (user == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        boolean isAuthor = Objects.equals(post.getUser().getUserId(), user.getUserId());
        if (!isAuthor && !hasBoardAdminAccess(post.getBoard(), user)) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }

    private boolean hasBoardAdminAccess(Board board, User user) {
        if (board == null || user == null) {
            return false;
        }
        if (user.getIsSuperAdmin()) {
            return true;
        }
        if (Objects.equals(board.getCreator().getUserId(), user.getUserId())) {
            return true;
        }
        return adminRepository.existsByUserAndBoardAndIsActive(user, board, true);
    }

    private boolean isInquiryBoard(Post post) {
        return post != null && isInquiryBoard(post.getBoard());
    }

    private boolean isInquiryBoard(Board board) {
        return board != null
                && board.getBoardUrl() != null
                && DEFAULT_INQUIRY_BOARD_URL.equalsIgnoreCase(board.getBoardUrl());
    }
}


