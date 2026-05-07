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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentQueryService {
    private static final Long EMPTY_BLOCKED_USER_ID_SENTINEL = -1L;
    private static final int DEFAULT_MY_COMMENT_PAGE_SIZE = 20;
    private static final Sort DEFAULT_MY_COMMENT_SORT = Sort.by(Sort.Order.desc("createdAt"));

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentPostAccessService commentPostAccessService;
    private final CommentReadSupport commentReadSupport;

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

        Map<Long, Long> replyCounts = commentReadSupport.loadVisibleReplyCounts(parentComments.getContent());
        List<CommentResponse> responseContent = parentComments.getContent().stream()
                .map(comment -> toMaskedCommentResponse(comment, context.blockedUserIds(), replyCounts))
                .toList();

        return new PageImpl<>(responseContent, pageable, parentComments.getTotalElements());
    }

    public CommentListResponse getReplies(Long parentId, Long currentUserId, Pageable pageable) {
        Comment parentComment = commentRepository.findByIdWithRelations(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        Page<Comment> replies = commentRepository.findRepliesWithRelations(parentId, false, pageable);
        if (Boolean.TRUE.equals(parentComment.getIsDeleted()) && replies.getTotalElements() == 0) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        CommentReadContext context = resolveReadContext(currentUserId);
        commentPostAccessService.validateReadable(parentComment.getPost(), context);

        Map<Long, Long> replyCounts = commentReadSupport.loadVisibleReplyCounts(replies.getContent());
        List<CommentResponse> maskedReplies = replies.getContent().stream()
                .map(comment -> toMaskedCommentResponse(comment, context.blockedUserIds(), replyCounts))
                .toList();

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
        Pageable safePageable = normalizeMyCommentPageable(pageable);
        CommentReadContext context = commentPostAccessService.resolveReadContext(user);
        Set<Long> blockedUserIds = context.blockedUserIds();
        List<Long> blockedUserIdParams = blockedUserIds.isEmpty()
                ? List.of(EMPTY_BLOCKED_USER_ID_SENTINEL)
                : List.copyOf(blockedUserIds);
        return commentRepository.findVisibleMyComments(
                user,
                Boolean.TRUE.equals(user.getIsSuperAdmin()),
                blockedUserIds.isEmpty(),
                blockedUserIdParams,
                safePageable)
                .map(MyCommentResponse::from);
    }

    private Pageable normalizeMyCommentPageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequestUtils.of(0, DEFAULT_MY_COMMENT_PAGE_SIZE, DEFAULT_MY_COMMENT_SORT);
        }
        return PageRequestUtils.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_MY_COMMENT_SORT);
    }

    private CommentReadContext resolveReadContext(Long currentUserId) {
        if (currentUserId == null) {
            return new CommentReadContext(null, Set.of());
        }
        User viewer = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return commentPostAccessService.resolveReadContext(viewer);
    }

    private CommentResponse maskCommentContent(CommentResponse response, Set<Long> blockedUserIds) {
        if (response.getAuthor() != null
                && blockedUserIds.contains(response.getAuthor().getUserId())) {
            return response.toBuilder()
                    .content(null)
                    .author(null)
                    .isBlockedAuthor(true)
                    .maskedAuthorId(response.getAuthor().getUserId())
                    .build();
        }
        return response;
    }

    private CommentResponse toMaskedCommentResponse(Comment comment, Set<Long> blockedUserIds) {
        return toMaskedCommentResponse(comment, blockedUserIds, Collections.emptyMap());
    }

    private CommentResponse toMaskedCommentResponse(Comment comment, Set<Long> blockedUserIds, Map<Long, Long> replyCounts) {
        long replyCount = replyCounts.getOrDefault(comment.getCommentId(), 0L);
        CommentResponse response = CommentResponse.from(comment).toBuilder()
                .replyCount(replyCount)
                .hasReplies(replyCount > 0)
                .build();
        return maskCommentContent(response, blockedUserIds);
    }
}
