package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.actor.ActorWritePort;
import com.weedrice.whiteboard.domain.actor.ContentActorRef;
import com.weedrice.whiteboard.domain.badge.service.BadgeEvaluationService;
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
import com.weedrice.whiteboard.domain.comment.port.CommentNotificationPort;
import com.weedrice.whiteboard.domain.comment.port.CommentPostPort;
import com.weedrice.whiteboard.domain.comment.port.CommentPostSnapshot;
import com.weedrice.whiteboard.domain.comment.port.CommentUserWritePort;
import com.weedrice.whiteboard.domain.notification.dto.CommentStreamEvent;
import com.weedrice.whiteboard.domain.notification.service.CommentStreamEventDispatcher;
import com.weedrice.whiteboard.domain.point.service.ContentRewardPolicy;
import com.weedrice.whiteboard.domain.point.service.ContentRewardService;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchEventPublisher;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchIndexAction;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.config.AnonymousReadCacheInvalidator;
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
    private final CommentLikeRepository commentLikeRepository;
    private final CommentVersionRepository commentVersionRepository;
    private final CommentClosureRepository commentClosureRepository;
    private final CommentMentionRepository commentMentionRepository;
    private final ActorWritePort actorWritePort;
    private final CommentUserWritePort commentUserWritePort;
    private final CommentPostPort commentPostPort;
    private final ContentRewardService contentRewardService;
    private final CommentNotificationPort commentNotificationPort;
    private final CommentStreamEventDispatcher commentStreamEventDispatcher;
    private final SemanticSearchEventPublisher semanticSearchEventPublisher;
    private final CommentLikeCommand commentLikeCommand;
    private final BadgeEvaluationService badgeEvaluationService;
    private final AnonymousReadCacheInvalidator anonymousReadCacheInvalidator;

    @Transactional
    public Long createComment(CommentCreateCommand command) {
        return createCommentWithResponse(command).getCommentId();
    }

    @Transactional
    public void lockAuthorForWrite(Long userId) {
        commentUserWritePort.validateWritable(userId);
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

        commentUserWritePort.validateWritable(userId);
        resolveActor(userId, agentId, context);
        CommentPostSnapshot post = resolvePostForCreate(postId, context);
        if (context == null || !context.postReadablePrevalidated()) {
            validatePostReadable(post.postId(), userId);
        }
        commentPostPort.validateWritable(post.postId(), userId);

        Comment parentComment = null;
        int depth = 0;
        if (parentId != null) {
            parentComment = resolveParentComment(parentId, post, context);
            depth = parentComment.getDepth() + 1;
        }

        String sanitizedContent = sanitizeCommentContent(content);

        Comment comment = Comment.builder()
                .postId(post.postId())
                .userId(userId)
                .agentId(agentId)
                .parent(parentComment)
                .depth(depth)
                .content(sanitizedContent)
                .build();

        Comment savedComment = commentRepository.save(comment);
        commentPostPort.incrementCommentCount(post.postId());
        saveCommentVersion(savedComment, userId, "CREATE", null);
        replaceCommentMentions(savedComment, userId, mentionedUserIds);

        if (parentId != null) {
            commentClosureRepository.createClosures(savedComment.getCommentId(), parentId);
        } else {
            commentClosureRepository.createSelfClosure(savedComment.getCommentId());
        }

        int earnedPoints = contentRewardService.rewardCreate(userId, savedComment.getCommentId(),
                ContentRewardPolicy.COMMENT);
        if (parentComment != null) {
            commentNotificationPort.publishReply(userId, agentId, parentComment.getUserId(), parentId);
        } else {
            commentNotificationPort.publishCreate(userId, agentId, post.ownerUserId(), postId);
        }
        commentUserWritePort.publishMentions(
                userId, agentId, savedComment.getCommentId(), content, mentionedUserIds);
        semanticSearchEventPublisher.publish("COMMENT", savedComment.getCommentId(), SemanticSearchIndexAction.UPSERT);
        publishCommentStreamEvent("CREATED", post.postId(), savedComment.getCommentId(), userId);
        anonymousReadCacheInvalidator.evictPostEngagementCachesAfterCommit(post.boardUrl());
        badgeEvaluationService.evaluateCommentCountBadges(userId);

        return CommentCreateResponse.builder()
                .commentId(savedComment.getCommentId())
                .earnedPoints(earnedPoints > 0 ? earnedPoints : null)
                .build();
    }

    private void resolveActor(Long userId, Long agentId, CommentCreateContext context) {
        if (context != null && context.agentId() != null) {
            if (!Objects.equals(context.agentId(), agentId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
        actorWritePort.validateForWrite(new ContentActorRef(userId, agentId));
    }

    private CommentPostSnapshot resolvePostForCreate(Long postId, CommentCreateContext context) {
        if (context != null && context.postId() != null) {
            if (!Objects.equals(context.postId(), postId)) {
                throw new BusinessException(ErrorCode.POST_NOT_FOUND);
            }
        }
        return commentPostPort.lockForWrite(postId);
    }

    private Comment resolveParentComment(Long parentId, CommentPostSnapshot post, CommentCreateContext context) {
        Comment parentComment = context != null && context.parentComment() != null
                ? context.parentComment()
                : commentRepository.findByIdWithRelationsForUpdate(parentId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        validateParentComment(parentComment, parentId, post);
        return parentComment;
    }

    private void validateParentComment(Comment parentComment, Long parentId, CommentPostSnapshot post) {
        if (parentComment == null
                || !Objects.equals(parentComment.getCommentId(), parentId)
                || parentComment.getIsDeleted()
                || Boolean.TRUE.equals(parentComment.getIsBlinded())
                || !Objects.equals(parentComment.getPostId(), post.postId())) {
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

        commentUserWritePort.validateWritable(userId);
        LockedCommentTarget target = loadCommentTargetForUpdate(commentId);
        Comment comment = target.comment();
        commentPostPort.validateWritable(target.post().postId(), userId);
        validateReadableActiveComment(target.post().postId(), comment, userId);
        validateCommentOwner(comment, userId);

        String originalContent = comment.getContent();
        String sanitizedContent = sanitizeCommentContent(content);
        comment.updateContent(sanitizedContent);

        saveCommentVersion(comment, userId, "MODIFY", originalContent);
        if (mentionedUserIds != null) {
            Set<Long> previousMentionedUserIds = loadStoredMentionUserIds(comment.getCommentId());
            List<Long> storedMentionedUserIds = replaceCommentMentions(comment, userId, mentionedUserIds);
            commentUserWritePort.publishNewMentions(
                    userId,
                    comment.getAgentId(),
                    comment.getCommentId(),
                    previousMentionedUserIds,
                    storedMentionedUserIds);
        }
        semanticSearchEventPublisher.publish("COMMENT", comment.getCommentId(), SemanticSearchIndexAction.UPSERT);
        publishCommentStreamEvent("UPDATED", target.post().postId(), comment.getCommentId(), userId);
        return comment.getCommentId();
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        commentUserWritePort.validateWritable(userId);
        LockedCommentTarget target = loadCommentTargetForUpdate(commentId);
        Comment comment = target.comment();
        commentPostPort.validateWritable(target.post().postId(), userId);
        validateReadableExistingComment(target.post().postId(), comment, userId);
        validateCommentOwner(comment, userId);

        String originalContent = comment.getContent();
        comment.deleteComment();
        Long postId = comment.getPostId();
        commentPostPort.decrementCommentCount(postId);

        saveCommentVersion(comment, userId, "DELETE", originalContent);
        contentRewardService.rollbackCreateReward(userId, commentId, ContentRewardPolicy.COMMENT);
        semanticSearchEventPublisher.publish("COMMENT", comment.getCommentId(), SemanticSearchIndexAction.DELETE);
        publishCommentStreamEvent("DELETED", postId, comment.getCommentId(), userId);
        anonymousReadCacheInvalidator.evictPostEngagementCachesAfterCommit(
                target.post().boardUrl());
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

    @Transactional
    public void likeComment(Long userId, Long commentId) {
        commentUserWritePort.validateWritable(userId);
        LockedCommentTarget target = loadCommentTargetForUpdate(commentId);
        commentPostPort.validateWritable(target.post().postId(), userId);
        validateReadableActiveComment(target.post().postId(), target.comment(), userId);

        commentLikeCommand.like(userId, target.comment(), CommentLikeCommand.DuplicatePolicy.THROW_ALREADY_LIKED);
        commentNotificationPort.publishLike(userId, target.comment().getUserId(), commentId);
    }

    @Transactional
    public void unlikeComment(Long userId, Long commentId) {
        commentUserWritePort.validateWritable(userId);
        LockedCommentTarget target = loadCommentTargetForUpdate(commentId);
        commentPostPort.validateWritable(target.post().postId(), userId);
        validateReadableExistingComment(target.post().postId(), target.comment(), userId);

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
        // Do not manage the comment until parent locks are held: a lock query does not refresh cached state.
        Long postId = commentRepository.findPostIdByCommentId(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        CommentPostSnapshot post = commentPostPort.lockForWrite(postId);
        Comment comment = commentRepository.findByIdWithRelationsForUpdate(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (!Objects.equals(comment.getPostId(), post.postId())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        return new LockedCommentTarget(post, comment);
    }

    private void validateReadableActiveComment(Long postId, Comment comment, Long userId) {
        validateReadableExistingComment(postId, comment, userId);
        if (Boolean.TRUE.equals(comment.getIsBlinded())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }

    private void validateReadableExistingComment(Long postId, Comment comment, Long userId) {
        validatePostReadable(postId, userId);
        if (Boolean.TRUE.equals(comment.getIsDeleted())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }

    private void validateCommentOwner(Comment comment, Long userId) {
        if (!comment.getUserId().equals(userId)) {
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

    private void saveCommentVersion(Comment comment, Long modifierUserId, String versionType, String originalContent) {
        CommentVersion commentVersion = CommentVersion.builder()
                .comment(comment)
                .modifierId(modifierUserId)
                .versionType(versionType)
                .originalContent(originalContent)
                .build();
        commentVersionRepository.save(commentVersion);
    }

    private List<Long> replaceCommentMentions(Comment comment, Long authorUserId, Collection<Long> mentionedUserIds) {
        commentMentionRepository.deleteByCommentCommentId(comment.getCommentId());
        List<Long> resolvedMentionedUserIds = commentUserWritePort.resolveMentionedUserIds(
                authorUserId, mentionedUserIds);
        if (resolvedMentionedUserIds.isEmpty()) {
            return List.of();
        }
        List<CommentMention> mentions = resolvedMentionedUserIds.stream()
                .map(userId -> CommentMention.builder()
                        .comment(comment)
                        .userId(userId)
                        .build())
                .toList();
        commentMentionRepository.saveAll(mentions);
        return resolvedMentionedUserIds;
    }

    private Set<Long> loadStoredMentionUserIds(Long commentId) {
        return commentMentionRepository.findByCommentCommentIdIn(List.of(commentId)).stream()
                .map(CommentMention::getUserId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private void validatePostReadable(Long postId, Long viewerUserId) {
        commentPostPort.validateReadable(postId, viewerUserId);
    }

    private record LockedCommentTarget(CommentPostSnapshot post, Comment comment) {
    }
}
