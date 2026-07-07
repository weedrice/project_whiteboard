package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentOwnershipService;
import com.weedrice.whiteboard.domain.comment.constant.CommentConstraints;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.entity.CommentVersion;
import com.weedrice.whiteboard.domain.comment.repository.CommentClosureRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentLikeRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentVersionRepository;
import com.weedrice.whiteboard.domain.point.service.ContentRewardPolicy;
import com.weedrice.whiteboard.domain.point.service.ContentRewardService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAuthorCommandPolicy;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchEventPublisher;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchIndexAction;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CommentCommandService {

    private static final int MAX_COMMENT_DEPTH = 5;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final CommentVersionRepository commentVersionRepository;
    private final CommentClosureRepository commentClosureRepository;
    private final AgentOwnershipService agentOwnershipService;
    private final UserWritableResolver userWritableResolver;
    private final SanctionService sanctionService;
    private final CommentPostAccessService commentPostAccessService;
    private final PostAuthorCommandPolicy postAuthorCommandPolicy;
    private final ContentRewardService contentRewardService;
    private final CommentNotificationService commentNotificationService;
    private final SemanticSearchEventPublisher semanticSearchEventPublisher;
    private final CommentLikeCommand commentLikeCommand;

    @Transactional
    public Long createComment(Long userId, Long postId, Long parentId, String content) {
        return createComment(userId, null, postId, parentId, content);
    }

    @Transactional
    public Long createCommentAsAgent(Long userId, Long agentId, Long postId, Long parentId, String content) {
        return createComment(userId, agentId, postId, parentId, content, null);
    }

    @Transactional
    public Long createCommentAsAgent(Long userId, Long agentId, Long postId, Long parentId, String content,
            CommentCreateContext context) {
        return createComment(userId, agentId, postId, parentId, content, context);
    }

    @Transactional
    public Long createComment(Long userId, Long agentId, Long postId, Long parentId, String content) {
        return createComment(userId, agentId, postId, parentId, content, null);
    }

    private Long createComment(Long userId, Long agentId, Long postId, Long parentId, String content,
            CommentCreateContext context) {
        User user = userWritableResolver.resolve(userId);
        sanctionService.validateNotMuted(user);
        Agent agent = resolveAgent(userId, agentId, context);
        Post post = resolvePost(postId, context);
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

        if (parentId != null) {
            commentClosureRepository.createClosures(savedComment.getCommentId(), parentId);
        } else {
            commentClosureRepository.createSelfClosure(savedComment.getCommentId());
        }

        contentRewardService.rewardCreate(userId, savedComment.getCommentId(), ContentRewardPolicy.COMMENT);
        if (parentComment != null) {
            commentNotificationService.publishReplyNotification(user, agent, parentComment, parentId);
        } else {
            commentNotificationService.publishCreateNotification(user, agent, post, postId);
        }
        semanticSearchEventPublisher.publish("COMMENT", savedComment.getCommentId(), SemanticSearchIndexAction.UPSERT);

        return savedComment.getCommentId();
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

    private Post resolvePost(Long postId, CommentCreateContext context) {
        if (context != null && context.post() != null) {
            Post contextPost = context.post();
            if (!Objects.equals(contextPost.getPostId(), postId)) {
                throw new BusinessException(ErrorCode.POST_NOT_FOUND);
            }
            return contextPost;
        }
        return postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
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
                || parentComment.getPost() == null
                || !Objects.equals(parentComment.getPost().getPostId(), post.getPostId())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        if (parentComment.getDepth() >= MAX_COMMENT_DEPTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    @Transactional
    public Long updateComment(Long userId, Long commentId, String content) {
        Comment comment = loadCommentForUpdate(commentId);
        User user = userWritableResolver.resolve(userId);
        sanctionService.validateNotMuted(user);
        validateReadableActiveComment(comment, user);
        validateCommentOwner(comment, userId);

        String originalContent = comment.getContent();
        String sanitizedContent = sanitizeCommentContent(content);
        comment.updateContent(sanitizedContent);

        saveCommentVersion(comment, user, "MODIFY", originalContent);
        semanticSearchEventPublisher.publish("COMMENT", comment.getCommentId(), SemanticSearchIndexAction.UPSERT);
        return comment.getCommentId();
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = loadCommentForUpdate(commentId);
        User user = userWritableResolver.resolve(userId);
        validateReadableActiveComment(comment, user);
        validateCommentOwner(comment, userId);

        String originalContent = comment.getContent();
        comment.deleteComment();
        decrementPostCommentCount(comment.getPost().getPostId());

        saveCommentVersion(comment, user, "DELETE", originalContent);
        contentRewardService.rollbackCreateReward(user, commentId, ContentRewardPolicy.COMMENT);
        semanticSearchEventPublisher.publish("COMMENT", comment.getCommentId(), SemanticSearchIndexAction.DELETE);
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
        Comment comment = loadReadableActiveCommentForReaction(commentId, user);

        commentLikeCommand.like(user, comment, CommentLikeCommand.DuplicatePolicy.THROW_ALREADY_LIKED);
        commentNotificationService.publishLikeNotification(user, comment, commentId);
    }

    @Transactional
    public void unlikeComment(Long userId, Long commentId) {
        User user = userWritableResolver.resolve(userId);
        sanctionService.validateNotMuted(user);
        loadReadableActiveCommentForReaction(commentId, user);

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

    private Comment loadCommentForUpdate(Long commentId) {
        return commentRepository.findByIdWithRelationsForUpdate(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
    }

    private Comment loadReadableActiveCommentForReaction(Long commentId, User user) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        validateReadableActiveComment(comment, user);
        return comment;
    }

    private void validateReadableActiveComment(Comment comment, User user) {
        validatePostReadable(comment.getPost(), user);
        if (comment.getIsDeleted()) {
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

    private void validatePostReadable(Post post, User viewer) {
        commentPostAccessService.validateReadable(post, commentPostAccessService.resolveReadContext(viewer));
    }
}
