package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.entity.CommentLike;
import com.weedrice.whiteboard.domain.comment.entity.CommentLikeId;
import com.weedrice.whiteboard.domain.comment.entity.CommentVersion;
import com.weedrice.whiteboard.domain.comment.entity.CommentClosure;
import com.weedrice.whiteboard.domain.comment.repository.CommentClosureRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentLikeRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentVersionRepository;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService; // Import UserBlockService
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
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
    private final UserBlockService userBlockService; // Inject UserBlockService

    public Page<CommentResponse> getComments(Long postId, Long currentUserId, Pageable pageable) {
        List<Long> blockedUserIds = null;
        if (currentUserId != null) {
            blockedUserIds = userBlockService.getBlockedUserIds(currentUserId);
        }

        final List<Long> finalBlockedUserIds = blockedUserIds; // For use in lambda

        Page<Comment> parentComments = commentRepository
                .findParentsWithChildrenOrNotDeleted(postId, pageable);
        List<Long> parentIds = parentComments.getContent().stream()
                .map(Comment::getCommentId)
                .collect(Collectors.toList());

        List<Comment> childComments = commentRepository
                .findByParent_CommentIdInAndIsDeletedOrderByCreatedAtAsc(parentIds, false);

        Map<Long, List<CommentResponse>> childCommentMap = childComments.stream()
                .map(comment -> maskCommentContent(CommentResponse.from(comment), finalBlockedUserIds))
                .collect(Collectors.groupingBy(commentResponse -> commentResponse.getParentId(),
                        Collectors.mapping(commentResponse -> commentResponse, Collectors.toList())));

        List<CommentResponse> responseContent = parentComments.getContent().stream()
                .map(comment -> {
                    CommentResponse response = maskCommentContent(CommentResponse.from(comment), finalBlockedUserIds);
                    if (childCommentMap.containsKey(comment.getCommentId())) {
                        response.setChildren(childCommentMap.get(comment.getCommentId()));
                    }
                    return response;
                })
                .collect(Collectors.toList());

        return new PageImpl<>(responseContent, pageable, parentComments.getTotalElements());
    }

    public Page<Comment> getReplies(Long parentId, Pageable pageable) {
        return commentRepository.findByParent_CommentIdAndIsDeletedOrderByCreatedAtAsc(parentId, false, pageable);
    }

    public CommentResponse getComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        return CommentResponse.from(comment);
    }

    public Page<Comment> getMyComments(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return commentRepository.findByUserAndIsDeletedOrderByCreatedAtDesc(user, false, pageable);
    }

    @Transactional
    public Comment createComment(Long userId, Long postId, Long parentId, String content) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (post.getIsDeleted()) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        Comment parentComment = null;
        int depth = 0;
        if (parentId != null) {
            parentComment = commentRepository.findById(parentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
            
            if (parentComment.getIsDeleted()) {
                throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
            }

            depth = parentComment.getDepth() + 1;
        }

        Comment comment = Comment.builder()
                .post(post)
                .user(user)
                .parent(parentComment)
                .depth(depth)
                .content(content)
                .build();

        post.incrementCommentCount();
        Comment savedComment = commentRepository.save(comment);
        
        // Save CommentVersion for CREATE
        saveCommentVersion(savedComment, user, "CREATE", null);

        // Save to CommentClosure
        if (parentId != null) {
            commentClosureRepository.createClosures(savedComment.getCommentId(), parentId);
        } else {
            commentClosureRepository.createSelfClosure(savedComment.getCommentId());
        }

        pointService.addPoint(userId, 10, "댓글 작성", savedComment.getCommentId(), "COMMENT");

        if (parentComment != null) {
            String notificationContent = user.getDisplayName() + "님이 회원님의 댓글에 답글을 남겼습니다.";
            NotificationEvent event = new NotificationEvent(parentComment.getUser(), user, "REPLY", "COMMENT", parentId,
                    notificationContent);
            eventPublisher.publishEvent(event);
        } else {
            String notificationContent = user.getDisplayName() + "님이 회원님의 게시글에 댓글을 남겼습니다.";
            NotificationEvent event = new NotificationEvent(post.getUser(), user, "COMMENT", "POST", postId,
                    notificationContent);
            eventPublisher.publishEvent(event);
        }

        return savedComment;
    }

    @Transactional
    public Comment updateComment(Long userId, Long commentId, String content) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        if (comment.getIsDeleted()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        if (!comment.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        String originalContent = comment.getContent(); // Get original content before update
        comment.updateContent(content);

        // Save CommentVersion for MODIFY
        saveCommentVersion(comment, userRepository.findById(userId)
                                                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)),
                            "MODIFY", originalContent);
        return comment;
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        if (comment.getIsDeleted()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        if (!comment.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        String originalContent = comment.getContent(); // Get content before delete
        comment.deleteComment();
        comment.getPost().decrementCommentCount();

        // Save CommentVersion for DELETE
        saveCommentVersion(comment, userRepository.findById(userId)
                                                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)),
                            "DELETE", originalContent);

        pointService.forceSubtractPoint(userId, 10, "댓글 삭제", commentId, "COMMENT");
    }

    @Transactional
    public void likeComment(Long userId, Long commentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

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

        String content = user.getDisplayName() + "님이 회원님의 댓글을 좋아합니다.";
        NotificationEvent event = new NotificationEvent(comment.getUser(), user, "LIKE", "COMMENT", commentId, content);
        eventPublisher.publishEvent(event);
    }

    @Transactional
    public void unlikeComment(Long userId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

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

    private CommentResponse maskCommentContent(CommentResponse response, List<Long> blockedUserIds) {
        if (blockedUserIds != null && response.getAuthor() != null && blockedUserIds.contains(response.getAuthor().getUserId())) {
            return response.toBuilder() // Use toBuilder to create a new builder from existing values
                    .content("차단된 사용자의 댓글입니다.")
                    .author(CommentResponse.AuthorInfo.builder()
                                .userId(response.getAuthor().getUserId())
                                .displayName("차단된 사용자")
                                .profileImageUrl(null)
                                .build())
                    .build();
        }
        return response;
    }
}