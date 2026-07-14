package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.comment.dto.CommentListResponse;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.dto.MyCommentResponse;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.entity.CommentMention;
import com.weedrice.whiteboard.domain.comment.repository.CommentMentionRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.user.service.UserReadableResolver;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Collection;
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
    private static final int BEST_COMMENT_LIMIT = 3;
    private static final int BEST_COMMENT_MIN_LIKES = 1;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserBlockRepository userBlockRepository;
    private final UserReadableResolver userReadableResolver;
    private final CommentPostAccessService commentPostAccessService;
    private final CommentReadSupport commentReadSupport;
    private final CommentReadModelAssembler commentReadModelAssembler;
    private final CommentMentionRepository commentMentionRepository;
    private MessageSource messageSource;

    @Autowired
    void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    // Contract: /posts/{postId}/comments pages only parent comments; replies are fetched lazily via /comments/{id}/replies.
    public Page<CommentResponse> getComments(Long postId, Long currentUserId, Pageable pageable) {
        Objects.requireNonNull(pageable, "Pageable must not be null");
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        CommentReadContext context = resolveReadContext(currentUserId);
        commentPostAccessService.validateReadable(post, context);

        BlockedUserIdsParameter blockedUserIdsParameter = BlockedUserIdsParameter.from(context.blockedUserIds());
        Page<Comment> parentComments = findParentComments(
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
        attachMentions(responseContent);

        return new PageImpl<>(responseContent, pageable, parentComments.getTotalElements());
    }

    public List<CommentResponse> getBestComments(Long postId, Long currentUserId) {
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        CommentReadContext context = resolveReadContext(currentUserId);
        commentPostAccessService.validateReadable(post, context);

        BlockedUserIdsParameter blockedUserIdsParameter = BlockedUserIdsParameter.from(context.blockedUserIds());
        List<Comment> comments = commentRepository.findBestRootComments(
                postId,
                BEST_COMMENT_MIN_LIKES,
                blockedUserIdsParameter.empty(),
                blockedUserIdsParameter.ids(),
                PageRequest.of(0, BEST_COMMENT_LIMIT));
        Map<Long, Long> replyCounts = commentReadSupport.loadVisibleReplyCounts(
                comments,
                context.blockedUserIds());
        List<CommentResponse> responseContent = comments.stream()
                .map(comment -> toCommentResponse(commentReadModelAssembler.from(
                        comment,
                        context.blockedUserIds(),
                        replyCounts)))
                .toList();
        attachMentions(responseContent);
        return responseContent;
    }

    private Page<Comment> findParentComments(Long postId, boolean blockedUserIdsEmpty,
            Collection<Long> blockedUserIds, Pageable pageable) {
        Sort rootSort = CommentReadSorts.normalizeRootSort(pageable.getSort());
        if (CommentReadSorts.isLikeOrder(rootSort)) {
            return commentRepository.findParentsWithChildrenOrNotDeletedOrderByLikeCount(
                    postId,
                    blockedUserIdsEmpty,
                    blockedUserIds,
                    pageable);
        }
        if (CommentReadSorts.isNewest(rootSort)) {
            return commentRepository.findParentsWithChildrenOrNotDeletedOrderByCreatedAtDesc(
                    postId,
                    blockedUserIdsEmpty,
                    blockedUserIds,
                    pageable);
        }
        return commentRepository.findParentsWithChildrenOrNotDeleted(
                postId,
                blockedUserIdsEmpty,
                blockedUserIds,
                pageable);
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
        attachMentions(maskedReplies);

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
        Comment comment = commentReadSupport.getNonDeletedWithRelationsOrThrow(commentId);
        CommentReadContext context = resolveReadContext(currentUserId);
        commentPostAccessService.validateReadable(comment.getPost(), context);
        CommentResponse response = toCommentResponse(commentReadModelAssembler.from(comment, context.blockedUserIds()));
        attachMentions(List.of(response));
        return response;
    }

    public Page<MyCommentResponse> getMyComments(Long userId, Pageable pageable) {
        User user = userReadableResolver.resolve(userId);
        Pageable safePageable = PageRequestUtils.of(pageable, DEFAULT_MY_COMMENT_PAGE_SIZE, DEFAULT_MY_COMMENT_SORT);
        CommentReadContext context = resolveReadContext(user);
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

    public Page<MyCommentResponse> getPublicProfileComments(Long targetUserId, Long viewerUserId, Pageable pageable) {
        User user = userReadableResolver.resolveActive(targetUserId);
        Pageable safePageable = PageRequestUtils.of(pageable, DEFAULT_MY_COMMENT_PAGE_SIZE, DEFAULT_MY_COMMENT_SORT);
        if (isRestrictedByBlock(targetUserId, viewerUserId)) {
            return Page.empty(safePageable);
        }
        return commentRepository.findPublicProfileCommentsByUser(user, safePageable)
                .map(MyCommentResponse::from);
    }

    private boolean isRestrictedByBlock(Long targetUserId, Long viewerUserId) {
        return viewerUserId != null
                && !viewerUserId.equals(targetUserId)
                && userBlockRepository.existsEitherDirection(viewerUserId, targetUserId);
    }

    private boolean hasVisibleReply(Comment parentComment, Set<Long> blockedUserIds) {
        BlockedUserIdsParameter blockedUserIdsParameter = BlockedUserIdsParameter.from(blockedUserIds);
        return commentRepository.existsVisibleReplyByParentId(
                parentComment.getCommentId(),
                blockedUserIdsParameter.empty(),
                blockedUserIdsParameter.ids());
    }

    private CommentReadContext resolveReadContext(Long currentUserId) {
        if (currentUserId == null) {
            return new CommentReadContext(null, Set.of());
        }
        return resolveReadContext(userReadableResolver.resolve(currentUserId));
    }

    private CommentReadContext resolveReadContext(User viewer) {
        return commentPostAccessService.resolveReadContext(viewer);
    }

    private CommentResponse toCommentResponse(CommentReadModel model) {
        Comment comment = model.comment();
        CommentReadModel.Author author = model.author();
        boolean deleted = model.status() == CommentReadModel.Status.DELETED;
        boolean blockedAuthor = model.status() == CommentReadModel.Status.BLOCKED_AUTHOR;
        boolean blinded = model.status() == CommentReadModel.Status.BLINDED;

        return CommentResponse.builder()
                .commentId(comment.getCommentId())
                .parentId(comment.getParent() != null ? comment.getParent().getCommentId() : null)
                .content(resolveCommentResponseContent(model))
                .author(toCommentAuthor(author))
                .depth(comment.getDepth())
                .likeCount(comment.getLikeCount())
                .isDeleted(deleted)
                .isBlockedAuthor(blockedAuthor)
                .isBlinded(blinded)
                .blindReason(blinded ? comment.getBlindReason() : null)
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
            case DELETED -> messageSource == null
                    ? CommentResponse.DELETED_CONTENT
                    : messageSource.getMessage("comment.deleted-content", null, CommentResponse.DELETED_CONTENT,
                            LocaleContextHolder.getLocale());
            case BLINDED -> null;
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
                .representativeBadge(author.representativeBadge())
                .build();
    }

    private void attachMentions(List<CommentResponse> responses) {
        List<Long> commentIds = responses.stream()
                .filter(response -> !response.isDeleted() && !response.isBlockedAuthor())
                .map(CommentResponse::getCommentId)
                .filter(Objects::nonNull)
                .toList();
        if (commentIds.isEmpty()) {
            return;
        }

        List<CommentMention> mentionRows = commentMentionRepository.findByCommentCommentIdIn(commentIds);
        if (mentionRows == null || mentionRows.isEmpty()) {
            return;
        }

        Map<Long, List<CommentResponse.MentionInfo>> mentionsByCommentId = mentionRows.stream()
                .filter(mention -> mention.getComment() != null && mention.getComment().getCommentId() != null)
                .filter(mention -> mention.getUser() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        mention -> mention.getComment().getCommentId(),
                        java.util.stream.Collectors.mapping(this::toMentionInfo, java.util.stream.Collectors.toList())));

        responses.forEach(response -> response.setMentions(
                mentionsByCommentId.getOrDefault(response.getCommentId(), List.of())));
    }

    private CommentResponse.MentionInfo toMentionInfo(CommentMention mention) {
        User user = mention.getUser();
        return CommentResponse.MentionInfo.builder()
                .userId(user.getUserId())
                .displayName(user.getDisplayName())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }
}
