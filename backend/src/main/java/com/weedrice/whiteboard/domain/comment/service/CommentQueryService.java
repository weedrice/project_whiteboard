package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
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
    private static final int DEFAULT_MY_COMMENT_PAGE_SIZE = 20;
    private static final Sort DEFAULT_MY_COMMENT_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("commentId"));

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentPostAccessService commentPostAccessService;
    private final CommentReadSupport commentReadSupport;
    private final CommentReadModelAssembler commentReadModelAssembler;

    // Contract: /posts/{postId}/comments pages only parent comments; replies are fetched lazily via /comments/{id}/replies.
    public Page<CommentResponse> getComments(Long postId, Long currentUserId, Pageable pageable) {
        Objects.requireNonNull(pageable, "Pageable must not be null");
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        CommentReadContext context = resolveReadContext(currentUserId);
        commentPostAccessService.validateReadable(post, context);

        BlockedUserIdsParameter blockedUserIdsParameter = BlockedUserIdsParameter.from(context.blockedUserIds());
        Page<Comment> parentComments = commentRepository.findParentsWithChildrenOrNotDeleted(
                postId,
                blockedUserIdsParameter.empty(),
                blockedUserIdsParameter.ids(),
                pageable);
        if (parentComments.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, parentComments.getTotalElements());
        }

        Map<Long, Long> replyCounts = commentReadSupport.loadVisibleReplyCounts(
                parentComments.getContent(),
                context.blockedUserIds());
        List<CommentResponse> responseContent = parentComments.getContent().stream()
                .map(comment -> toCommentResponse(commentReadModelAssembler.from(
                        comment,
                        context.blockedUserIds(),
                        replyCounts)))
                .toList();

        return new PageImpl<>(responseContent, pageable, parentComments.getTotalElements());
    }

    public CommentListResponse getReplies(Long parentId, Long currentUserId, Pageable pageable) {
        Comment parentComment = commentRepository.findByIdWithRelations(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        CommentReadContext context = resolveReadContext(currentUserId);
        commentPostAccessService.validateReadable(parentComment.getPost(), context);

        BlockedUserIdsParameter blockedUserIdsParameter = BlockedUserIdsParameter.from(context.blockedUserIds());
        Page<Comment> replies = commentRepository.findRepliesWithRelations(
                parentId,
                false,
                blockedUserIdsParameter.empty(),
                blockedUserIdsParameter.ids(),
                pageable);
        if (commentReadSupport.isDeleted(parentComment)
                && !hasVisibleReply(parentComment, context.blockedUserIds())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        Map<Long, Long> replyCounts = commentReadSupport.loadVisibleReplyCounts(
                replies.getContent(),
                context.blockedUserIds());
        List<CommentResponse> maskedReplies = replies.getContent().stream()
                .map(comment -> toCommentResponse(commentReadModelAssembler.from(
                        comment,
                        context.blockedUserIds(),
                        replyCounts)))
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
        return toCommentResponse(commentReadModelAssembler.from(comment, context.blockedUserIds()));
    }

    public Page<MyCommentResponse> getMyComments(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Pageable safePageable = normalizeMyCommentPageable(pageable);
        CommentReadContext context = commentPostAccessService.resolveReadContext(user);
        Set<Long> blockedUserIds = context.blockedUserIds();
        BlockedUserIdsParameter blockedUserIdsParameter = BlockedUserIdsParameter.from(blockedUserIds);
        return commentRepository.findVisibleMyComments(
                user,
                user.isUsableSuperAdmin(),
                blockedUserIdsParameter.empty(),
                blockedUserIdsParameter.ids(),
                BoardPolicyConstants.INQUIRY_BOARD_URL,
                safePageable)
                .map(MyCommentResponse::from);
    }

    private boolean hasVisibleReply(Comment parentComment, Set<Long> blockedUserIds) {
        BlockedUserIdsParameter blockedUserIdsParameter = BlockedUserIdsParameter.from(blockedUserIds);
        return commentRepository.existsVisibleReplyByParentId(
                parentComment.getCommentId(),
                blockedUserIdsParameter.empty(),
                blockedUserIdsParameter.ids());
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

    private CommentResponse toCommentResponse(CommentReadModel model) {
        Comment comment = model.comment();
        CommentReadModel.Author author = model.author();
        boolean deleted = model.status() == CommentReadModel.Status.DELETED;
        boolean blockedAuthor = model.status() == CommentReadModel.Status.BLOCKED_AUTHOR;

        return CommentResponse.builder()
                .commentId(comment.getCommentId())
                .parentId(comment.getParent() != null ? comment.getParent().getCommentId() : null)
                .content(resolveCommentResponseContent(model))
                .author(toCommentAuthor(author))
                .depth(comment.getDepth())
                .likeCount(comment.getLikeCount())
                .isDeleted(deleted)
                .isBlockedAuthor(blockedAuthor)
                .maskedAuthorId(blockedAuthor ? model.maskedAuthorId() : null)
                .createdAt(comment.getCreatedAt())
                .postId(comment.getPost().getPostId())
                .boardUrl(comment.getPost().getBoard().getBoardUrl())
                .postTitle(comment.getPost().getTitle())
                .replyCount(model.replyCount())
                .hasReplies(model.hasReplies())
                .build();
    }

    private String resolveCommentResponseContent(CommentReadModel model) {
        return switch (model.status()) {
            case ACTIVE -> model.comment().getContent();
            case DELETED -> CommentResponse.DELETED_CONTENT;
            case BLOCKED_AUTHOR -> null;
        };
    }

    private CommentResponse.AuthorInfo toCommentAuthor(CommentReadModel.Author author) {
        if (author == null) {
            return null;
        }
        return CommentResponse.AuthorInfo.builder()
                .userId(author.userId())
                .agentId(author.agentId())
                .authorType(author.authorType())
                .displayName(author.displayName())
                .profileImageUrl(author.profileImageUrl())
                .build();
    }
}
