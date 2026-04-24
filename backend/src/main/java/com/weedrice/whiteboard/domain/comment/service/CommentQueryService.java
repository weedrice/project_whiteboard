package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.comment.dto.CommentListResponse;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.dto.MyCommentResponse;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentQueryService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentPostAccessService commentPostAccessService;

    // Contract: /posts/{postId}/comments pages only parent comments; replies are fetched lazily via /comments/{id}/replies.
    public Page<CommentResponse> getComments(Long postId, Long currentUserId, Pageable pageable) {
        Objects.requireNonNull(pageable, "Pageable must not be null");
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        CommentReadContext context = resolveReadContext(currentUserId);
        commentPostAccessService.validateReadable(post, context);

        Page<Comment> parentComments = commentRepository.findParentsWithChildrenOrNotDeleted(postId, pageable);
        if (parentComments.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, parentComments.getTotalElements());
        }

        Map<Long, Long> replyCounts = loadVisibleReplyCounts(parentComments.getContent());
        List<CommentResponse> responseContent = parentComments.getContent().stream()
                .map(comment -> toMaskedCommentResponse(comment, context.blockedUserIds(), replyCounts))
                .collect(Collectors.toList());

        return new PageImpl<>(responseContent, pageable, parentComments.getTotalElements());
    }

    public CommentListResponse getReplies(Long parentId, Long currentUserId, Pageable pageable) {
        Comment parentComment = commentRepository.findNonDeletedByIdWithRelations(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        CommentReadContext context = resolveReadContext(currentUserId);
        commentPostAccessService.validateReadable(parentComment.getPost(), context);

        Page<Comment> replies = commentRepository.findRepliesWithRelations(parentId, false, pageable);
        Map<Long, Long> replyCounts = loadVisibleReplyCounts(replies.getContent());
        List<CommentResponse> maskedReplies = replies.getContent().stream()
                .map(comment -> toMaskedCommentResponse(comment, context.blockedUserIds(), replyCounts))
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
        CommentReadContext context = resolveReadContext(currentUserId);
        commentPostAccessService.validateReadable(comment.getPost(), context);
        return toMaskedCommentResponse(comment, context.blockedUserIds());
    }

    public Page<MyCommentResponse> getMyComments(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return commentRepository.findByUserAndIsDeletedOrderByCreatedAtDesc(user, false, pageable)
                .map(MyCommentResponse::from);
    }

    private CommentReadContext resolveReadContext(Long currentUserId) {
        if (currentUserId == null) {
            return new CommentReadContext(null, null);
        }
        User viewer = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return commentPostAccessService.resolveReadContext(viewer);
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
        return toMaskedCommentResponse(comment, blockedUserIds, Collections.emptyMap());
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
            return Collections.emptyMap();
        }

        List<CommentRepository.ReplyCountProjection> replyCounts = commentRepository.countVisibleRepliesByParentIds(commentIds);
        if (replyCounts == null || replyCounts.isEmpty()) {
            return Collections.emptyMap();
        }

        return replyCounts.stream()
                .collect(Collectors.toMap(
                        CommentRepository.ReplyCountProjection::getParentId,
                        CommentRepository.ReplyCountProjection::getReplyCount,
                        (left, right) -> right));
    }
}
