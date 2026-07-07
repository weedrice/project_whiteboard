package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.comment.dto.CommentListResponse;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.dto.MyCommentResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private static final int DEFAULT_COMMENT_PAGE_SIZE = 20;

    private final CommentQueryService commentQueryService;
    private final CommentCommandService commentCommandService;

    public Page<CommentResponse> getComments(Long postId, Long currentUserId, Pageable pageable) {
        return commentQueryService.getComments(postId, currentUserId, normalizeReadPageable(pageable));
    }

    public CommentListResponse getReplies(Long parentId, Long currentUserId, Pageable pageable) {
        return commentQueryService.getReplies(parentId, currentUserId, normalizeReadPageable(pageable));
    }

    public List<CommentResponse> getBestComments(Long postId, Long currentUserId) {
        return commentQueryService.getBestComments(postId, currentUserId);
    }

    public CommentResponse getComment(Long commentId, Long currentUserId) {
        return commentQueryService.getComment(commentId, currentUserId);
    }

    public Page<MyCommentResponse> getMyComments(Long userId, Pageable pageable) {
        return commentQueryService.getMyComments(userId, pageable);
    }

    public Page<MyCommentResponse> getPublicProfileComments(Long targetUserId, Long viewerUserId, Pageable pageable) {
        return commentQueryService.getPublicProfileComments(targetUserId, viewerUserId, pageable);
    }

    public Long createComment(Long userId, Long postId, Long parentId, String content) {
        return commentCommandService.createComment(userId, postId, parentId, content);
    }

    public Long createComment(Long userId, Long postId, Long parentId, String content,
            Collection<Long> mentionedUserIds) {
        return commentCommandService.createComment(userId, postId, parentId, content, mentionedUserIds);
    }

    public Long createCommentAsAgent(Long userId, Long agentId, Long postId, Long parentId, String content) {
        return commentCommandService.createCommentAsAgent(userId, agentId, postId, parentId, content);
    }

    public Long createCommentAsAgent(Long userId, Long agentId, Long postId, Long parentId, String content,
            CommentCreateContext context) {
        return commentCommandService.createCommentAsAgent(userId, agentId, postId, parentId, content, context);
    }

    public Long createComment(Long userId, Long agentId, Long postId, Long parentId, String content) {
        return commentCommandService.createComment(userId, agentId, postId, parentId, content);
    }

    public Long updateComment(Long userId, Long commentId, String content) {
        return commentCommandService.updateComment(userId, commentId, content);
    }

    public Long updateComment(Long userId, Long commentId, String content, Collection<Long> mentionedUserIds) {
        return commentCommandService.updateComment(userId, commentId, content, mentionedUserIds);
    }

    public void deleteComment(Long userId, Long commentId) {
        commentCommandService.deleteComment(userId, commentId);
    }

    public void likeComment(Long userId, Long commentId) {
        commentCommandService.likeComment(userId, commentId);
    }

    public void unlikeComment(Long userId, Long commentId) {
        commentCommandService.unlikeComment(userId, commentId);
    }

    private Pageable normalizeReadPageable(Pageable pageable) {
        return PageRequestUtils.of(
                pageable,
                DEFAULT_COMMENT_PAGE_SIZE,
                CommentReadSorts.READ_ORDER,
                CommentReadSorts.ALLOWED_ROOT_SORT_PROPERTIES);
    }
}
