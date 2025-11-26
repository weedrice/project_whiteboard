package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.entity.CommentLike;
import com.weedrice.whiteboard.domain.comment.entity.CommentLikeId;
import com.weedrice.whiteboard.domain.comment.repository.CommentLikeRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
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

        post.incrementCommentCount(); // 게시글 댓글 수 증가
        return commentRepository.save(comment);
    }

    @Transactional
    public Comment updateComment(Long userId, Long commentId, String content) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        if (!comment.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN); // 작성자만 수정 가능
        }

        comment.updateContent(content);
        return comment;
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        // TODO: 관리자 권한 확인 로직 추가 필요
        if (!comment.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN); // 작성자만 삭제 가능
        }

        comment.deleteComment(); // Soft Delete
        comment.getPost().decrementCommentCount(); // 게시글 댓글 수 감소
    }

    @Transactional
    public Comment toggleCommentLike(Long userId, Long commentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        CommentLikeId commentLikeId = new CommentLikeId(userId, commentId);
        if (commentLikeRepository.existsById(commentLikeId)) {
            commentLikeRepository.deleteById(commentLikeId);
            comment.decrementLikeCount();
        } else {
            CommentLike commentLike = CommentLike.builder()
                    .user(user)
                    .comment(comment)
                    .build();
            commentLikeRepository.save(commentLike);
            comment.incrementLikeCount();
            // TODO: 작성자에게 알림 (notifications INSERT)
        }
        return comment;
    }
}
