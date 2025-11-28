package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.entity.CommentLike;
import com.weedrice.whiteboard.domain.comment.entity.CommentLikeId;
import com.weedrice.whiteboard.domain.comment.repository.CommentLikeRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
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
    private final ApplicationEventPublisher eventPublisher;
    private final com.weedrice.whiteboard.domain.point.service.PointService pointService;

    public Page<CommentResponse> getComments(Long postId, Pageable pageable) {
        Page<Comment> parentComments = commentRepository.findByPost_PostIdAndParentIsNullAndIsDeletedOrderByCreatedAtAsc(postId, "N", pageable);
        List<Long> parentIds = parentComments.getContent().stream()
                .map(Comment::getCommentId)
                .collect(Collectors.toList());

        List<Comment> childComments = commentRepository.findByParent_CommentIdInAndIsDeletedOrderByCreatedAtAsc(parentIds, "N");

        Map<Long, List<CommentResponse>> childCommentMap = childComments.stream()
                .collect(Collectors.groupingBy(comment -> comment.getParent().getCommentId(),
                        Collectors.mapping(CommentResponse::from, Collectors.toList())));

        List<CommentResponse> responseContent = parentComments.getContent().stream()
                .map(comment -> {
                    CommentResponse response = CommentResponse.from(comment);
                    if (childCommentMap.containsKey(comment.getCommentId())) {
                        response.setChildren(childCommentMap.get(comment.getCommentId()));
                    }
                    return response;
                })
                .collect(Collectors.toList());

        return new PageImpl<>(responseContent, pageable, parentComments.getTotalElements());
    }

    public Page<Comment> getReplies(Long parentId, Pageable pageable) {
        return commentRepository.findByParent_CommentIdAndIsDeletedOrderByCreatedAtAsc(parentId, "N", pageable);
    }

    public Page<Comment> getMyComments(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return commentRepository.findByUserAndIsDeletedOrderByCreatedAtDesc(user, "N", pageable);
    }

    @Transactional
    public Comment createComment(Long userId, Long postId, Long parentId, String content) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        Comment parentComment = null;
        Integer depth = 0;
        if (parentId != null) {
            parentComment = commentRepository.findById(parentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
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
        
        pointService.addPoint(userId, 10, "댓글 작성", savedComment.getCommentId(), "COMMENT");

        if (parentComment != null) {
            String notificationContent = user.getDisplayName() + "님이 회원님의 댓글에 답글을 남겼습니다.";
            NotificationEvent event = new NotificationEvent(parentComment.getUser(), user, "REPLY", "COMMENT", parentId, notificationContent);
            eventPublisher.publishEvent(event);
        } else {
            String notificationContent = user.getDisplayName() + "님이 회원님의 게시글에 댓글을 남겼습니다.";
            NotificationEvent event = new NotificationEvent(post.getUser(), user, "COMMENT", "POST", postId, notificationContent);
            eventPublisher.publishEvent(event);
        }

        return savedComment;
    }

    @Transactional
    public Comment updateComment(Long userId, Long commentId, String content) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        if (!comment.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        comment.updateContent(content);
        return comment;
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        if (!comment.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        comment.deleteComment();
        comment.getPost().decrementCommentCount();
        
        pointService.forceSubtractPoint(userId, 10, "댓글 삭제", commentId, "COMMENT");
    }

    @Transactional
    public void likeComment(Long userId, Long commentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        CommentLikeId commentLikeId = new CommentLikeId(userId, commentId);
        if (commentLikeRepository.existsById(commentLikeId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "이미 좋아요를 누른 댓글입니다.");
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
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "좋아요를 누르지 않은 댓글입니다.");
        }

        commentLikeRepository.deleteById(commentLikeId);
        comment.decrementLikeCount();
    }
}
