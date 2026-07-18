package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentOwnershipService;
import com.weedrice.whiteboard.domain.badge.service.BadgeEvaluationService;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.constant.CommentConstraints;
import com.weedrice.whiteboard.domain.comment.dto.CommentCreateResponse;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.entity.CommentMention;
import com.weedrice.whiteboard.domain.comment.entity.CommentVersion;
import com.weedrice.whiteboard.domain.comment.repository.CommentClosureRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentLikeRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentMentionRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentVersionRepository;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.dto.CommentStreamEvent;
import com.weedrice.whiteboard.domain.notification.service.CommentStreamEventDispatcher;
import com.weedrice.whiteboard.domain.notification.service.MentionService;
import com.weedrice.whiteboard.domain.point.service.ContentRewardPolicy;
import com.weedrice.whiteboard.domain.point.service.ContentRewardService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAuthorCommandPolicy;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchEventPublisher;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchIndexAction;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommentCommandService {

    private static final int MAX_COMMENT_DEPTH = 5;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final CommentVersionRepository commentVersionRepository;
    private final CommentClosureRepository commentClosureRepository;
    private final CommentMentionRepository commentMentionRepository;
    private final UserRepository userRepository;
    private final AgentOwnershipService agentOwnershipService;
    private final UserWritableResolver userWritableResolver;
    private final SanctionService sanctionService;
    private final CommentPostAccessService commentPostAccessService;
    private final PostAuthorCommandPolicy postAuthorCommandPolicy;
    private final ContentRewardService contentRewardService;
    private final CommentNotificationService commentNotificationService;
    private final MentionService mentionService;
    private final CommentStreamEventDispatcher commentStreamEventDispatcher;
    private final SemanticSearchEventPublisher semanticSearchEventPublisher;
    private final CommentLikeCommand commentLikeCommand;
    private final BadgeEvaluationService badgeEvaluationService;

    @Transactional
    public Long createComment(CommentCreateCommand command) {
        return createCommentWithResponse(command).getCommentId();
    }

    @Transactional
    public CommentCreateResponse createCommentWithResponse(CommentCreateCommand command) {
        Long userId = command.userId();
        Long agentId = command.agentId();
        Long postId = command.postId();
        Long parentId = command.parentId();
        String content = command.content();
        CommentCreateContext context = command.context();
        Collection<Long> mentionedUserIds = command.mentionedUserIds();

        User user = userWritableResolver.resolve(userId);
        sanctionService.validateNotMuted(user);
        Agent agent = resolveAgent(userId, agentId, context);
        Post post = resolvePostForCreate(postId, context);
        if (context == null || !context.postReadablePrevalidated()) {
            validatePostReadable(post, user);
        }
        postAuthorCommandPolicy.validateWritableCommand(post, user, post.getCategory());

        Comment parentComment = null;
        int depth = 0;
        if (parentId != null) {
            parentComment = resolveParentComment(parentId, post, context);
            depth = parentComment.getDepth() + 1;
        }

        String sanitizedContent = sanitizeCommentContent(content);

        Comment comment = Comment.builder()
                .post(post)
                .user(user)
                .agent(agent)
                .parent(parentComment)
                .depth(depth)
                .content(sanitizedContent)
                .build();

        Comment savedComment = commentRepository.save(comment);
        incrementPostCommentCount(post.getPostId());
        saveCommentVersion(savedComment, user, "CREATE", null);
        replaceCommentMentions(savedComment, mentionedUserIds);

        if (parentId != null) {
            commentClosureRepository.createClosures(savedComment.getCommentId(), parentId);
        } else {
            commentClosureRepository.createSelfClosure(savedComment.getCommentId());
        }

        int earnedPoints = contentRewardService.rewardCreate(userId, savedComment.getCommentId(),
                ContentRewardPolicy.COMMENT);
        if (parentComment != null) {
            commentNotificationService.publishReplyNotification(user, agent, parentComment, parentId);
        } else {
            commentNotificationService.publishCreateNotification(user, agent, post, postId);
        }
        publishMentionNotifications(user, agent, savedComment.getCommentId(), content, mentionedUserIds);
        semanticSearchEventPublisher.publish("COMMENT", savedComment.getCommentId(), SemanticSearchIndexAction.UPSERT);
        publishCommentStreamEvent("CREATED", post.getPostId(), savedComment.getCommentId(), userId);
        badgeEvaluationService.evaluateCommentCountBadges(userId);

        return CommentCreateResponse.builder()
                .commentId(savedComment.getCommentId())
                .earnedPoints(earnedPoints > 0 ? earnedPoints : null)
                .build();
    }

    private void publishMentionNotifications(User user, Agent agent, Long commentId, String content,
            Collection<Long> mentionedUserIds) {
        if (mentionedUserIds != null && !mentionedUserIds.isEmpty()) {
            mentionService.publishMentions(user, agent, NotificationSourceType.COMMENT, commentId, mentionedUserIds);
            return;
        }
        mentionService.publishMentions(user, agent, NotificationSourceType.COMMENT, commentId, content);
    }

    private Agent resolveAgent(Long userId, Long agentId, CommentCreateContext context) {
        if (context != null && context.agent() != null) {
            Agent contextAgent = context.agent();
            if (!Objects.equals(contextAgent.getAgentId(), agentId)
                    || contextAgent.getUser() == null
                    || !Objects.equals(contextAgent.getUser().getUserId(), userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            return context.agent();
        }
        return agentOwnershipService.resolveOwnedActiveAgent(userId, agentId);
    }

    private Post lockPostAndBoard(Long postId) {
        Post post = postRepository.findByIdWithRelationsForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (post.getBoard() == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        Long boardId = post.getBoard().getBoardId();
        if (boardId != null) {
            boardRepository.findByIdForUpdate(boardId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        }
        return post;
    }

    private Post resolvePostForCreate(Long postId, CommentCreateContext context) {
        if (context != null && context.post() != null) {
            if (!Objects.equals(context.post().getPostId(), postId)) {
                throw new BusinessException(ErrorCode.POST_NOT_FOUND);
            }
            return context.post();
        }
        return lockPostAndBoard(postId);
    }

    private Comment resolveParentComment(Long parentId, Post post, CommentCreateContext context) {
        Comment parentComment = context != null && context.parentComment() != null
                ? context.parentComment()
                : commentRepository.findByIdWithRelationsForUpdate(parentId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        validateParentComment(parentComment, parentId, post);
        return parentComment;
    }

    private void validateParentComment(Comment parentComment, Long parentId, Post post) {
        if (parentComment == null
                || !Objects.equals(parentComment.getCommentId(), parentId)
                || parentComment.getIsDeleted()
                || Boolean.TRUE.equals(parentComment.getIsBlinded())
                || parentComment.getPost() == null
                || !Objects.equals(parentComment.getPost().getPostId(), post.getPostId())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        if (parentComment.getDepth() >= MAX_COMMENT_DEPTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    @Transactional
    public Long updateComment(CommentUpdateCommand command) {
        Long userId = command.userId();
        Long commentId = command.commentId();
        String content = command.content();
        Collection<Long> mentionedUserIds = command.mentionedUserIds();

        LockedCommentTarget target = loadCommentTargetForUpdate(commentId);
        Comment comment = target.comment();
        User user = userWritableResolver.resolve(userId);
        sanctionService.validateNotMuted(user);
        validateReadableActiveComment(target.post(), comment, user);
        validateCommentOwner(comment, userId);

        String originalContent = comment.getContent();
        String sanitizedContent = sanitizeCommentContent(content);
        comment.updateContent(sanitizedContent);

        saveCommentVersion(comment, user, "MODIFY", originalContent);
        if (mentionedUserIds != null) {
            replaceCommentMentions(comment, mentionedUserIds);
        }
        semanticSearchEventPublisher.publish("COMMENT", comment.getCommentId(), SemanticSearchIndexAction.UPSERT);
        return comment.getCommentId();
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        LockedCommentTarget target = loadCommentTargetForUpdate(commentId);
        Comment comment = target.comment();
        User user = userWritableResolver.resolve(userId);
        validateReadableExistingComment(target.post(), comment, user);
        validateCommentOwner(comment, userId);

        String originalContent = comment.getContent();
        comment.deleteComment();
        Long postId = comment.getPost().getPostId();
        decrementPostCommentCount(postId);

        saveCommentVersion(comment, user, "DELETE", originalContent);
        contentRewardService.rollbackCreateReward(user, commentId, ContentRewardPolicy.COMMENT);
        semanticSearchEventPublisher.publish("COMMENT", comment.getCommentId(), SemanticSearchIndexAction.DELETE);
        publishCommentStreamEvent("DELETED", postId, comment.getCommentId(), userId);
    }

    private void publishCommentStreamEvent(String action, Long postId, Long commentId, Long actorUserId) {
        commentStreamEventDispatcher.publishAfterCommit(CommentStreamEvent.builder()
                .action(action)
                .postId(postId)
                .commentId(commentId)
                .actorUserId(actorUserId)
                .occurredAt(LocalDateTime.now())
                .build());
    }

    private void incrementPostCommentCount(Long postId) {
        if (postRepository.incrementCommentCount(postId) == 0) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }

    private void decrementPostCommentCount(Long postId) {
        if (postRepository.decrementCommentCount(postId) == 0) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }

    @Transactional
    public void likeComment(Long userId, Long commentId) {
        User user = userWritableResolver.resolve(userId);
        sanctionService.validateNotMuted(user);
        LockedCommentTarget target = loadCommentTargetForUpdate(commentId);
        validateReadableActiveComment(target.post(), target.comment(), user);

        commentLikeCommand.like(user, target.comment(), CommentLikeCommand.DuplicatePolicy.THROW_ALREADY_LIKED);
        commentNotificationService.publishLikeNotification(user, target.comment(), commentId);
    }

    @Transactional
    public void unlikeComment(Long userId, Long commentId) {
        User user = userWritableResolver.resolve(userId);
        sanctionService.validateNotMuted(user);
        LockedCommentTarget target = loadCommentTargetForUpdate(commentId);
        validateReadableExistingComment(target.post(), target.comment(), user);

        int deletedCount = commentLikeRepository.deleteByUserIdAndCommentId(userId, commentId);
        if (deletedCount == 0) {
            throw new BusinessException(ErrorCode.NOT_LIKED);
        }

        decrementCommentLikeCount(commentId);
    }

    private void decrementCommentLikeCount(Long commentId) {
        if (commentRepository.decrementLikeCount(commentId) == 0) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }

    private LockedCommentTarget loadCommentTargetForUpdate(Long commentId) {
        Post post = postRepository.findByCommentIdWithRelationsForUpdate(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (post.getBoard() == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        Long boardId = post.getBoard().getBoardId();
        if (boardId != null) {
            boardRepository.findByIdForUpdate(boardId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        }
        Comment comment = commentRepository.findByIdWithRelationsForUpdate(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (comment.getPost() == null || !Objects.equals(comment.getPost().getPostId(), post.getPostId())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        return new LockedCommentTarget(post, comment);
    }

    private void validateReadableActiveComment(Post lockedPost, Comment comment, User user) {
        validateReadableExistingComment(lockedPost, comment, user);
        if (Boolean.TRUE.equals(comment.getIsBlinded())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }

    private void validateReadableExistingComment(Post lockedPost, Comment comment, User user) {
        validatePostReadable(lockedPost, user);
        if (Boolean.TRUE.equals(comment.getIsDeleted())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }

    private void validateCommentOwner(Comment comment, Long userId) {
        if (!comment.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private String sanitizeCommentContent(String content) {
        String sanitizedContent = InputSanitizer.stripHtml(content);
        if (sanitizedContent == null || sanitizedContent.isBlank()
                || sanitizedContent.length() > CommentConstraints.MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return sanitizedContent;
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

    private void replaceCommentMentions(Comment comment, Collection<Long> mentionedUserIds) {
        commentMentionRepository.deleteByCommentCommentId(comment.getCommentId());
        List<User> mentionedUsers = loadMentionedUsers(mentionedUserIds);
        if (mentionedUsers.isEmpty()) {
            return;
        }
        List<CommentMention> mentions = mentionedUsers.stream()
                .map(user -> CommentMention.builder()
                        .comment(comment)
                        .user(user)
                        .build())
                .toList();
        commentMentionRepository.saveAll(mentions);
    }

    private List<User> loadMentionedUsers(Collection<Long> mentionedUserIds) {
        if (mentionedUserIds == null || mentionedUserIds.isEmpty()) {
            return List.of();
        }
        Set<Long> uniqueIds = mentionedUserIds.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (uniqueIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllById(uniqueIds).stream()
                .filter(user -> User.STATUS_ACTIVE.equals(user.getStatus()))
                .filter(user -> user.getDeletedAt() == null)
                .limit(10)
                .toList();
    }

    private void validatePostReadable(Post post, User viewer) {
        commentPostAccessService.validateReadable(post, commentPostAccessService.resolveReadContext(viewer));
    }

    private record LockedCommentTarget(Post post, Comment comment) {
    }
}
