package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentOwnershipService;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.entity.CommentLike;
import com.weedrice.whiteboard.domain.comment.entity.CommentVersion;
import com.weedrice.whiteboard.domain.comment.repository.CommentClosureRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentLikeRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentVersionRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CommentCommandService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final CommentVersionRepository commentVersionRepository;
    private final CommentClosureRepository commentClosureRepository;
    private final AgentOwnershipService agentOwnershipService;
    private final SanctionService sanctionService;
    private final CommentPostAccessService commentPostAccessService;
    private final CommentRewardService commentRewardService;
    private final CommentNotificationService commentNotificationService;

    @Transactional
    public Comment createComment(Long userId, Long postId, Long parentId, String content) {
        return createComment(userId, null, postId, parentId, content);
    }

    @Transactional
    public Comment createCommentAsAgent(Long userId, Long agentId, Long postId, Long parentId, String content) {
        return createComment(userId, agentId, postId, parentId, content);
    }

    @Transactional
    public Comment createComment(Long userId, Long agentId, Long postId, Long parentId, String content) {
        User user = getWritableUser(userId);
        sanctionService.validateNotMuted(user);
        Agent agent = agentOwnershipService.resolveOwnedActiveAgent(userId, agentId);
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        validatePostReadable(post, user);

        Comment parentComment = null;
        int depth = 0;
        if (parentId != null) {
            parentComment = commentRepository.findById(parentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

            if (parentComment.getIsDeleted()) {
                throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
            }
            if (!Objects.equals(parentComment.getPost().getPostId(), post.getPostId())) {
                throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
            }

            depth = parentComment.getDepth() + 1;
        }

        String sanitizedContent = InputSanitizer.stripHtml(content);

        Comment comment = Comment.builder()
                .post(post)
                .user(user)
                .agent(agent)
                .parent(parentComment)
                .depth(depth)
                .content(sanitizedContent)
                .build();

        Comment savedComment = commentRepository.save(comment);
        postRepository.incrementCommentCount(post.getPostId());
        saveCommentVersion(savedComment, user, "CREATE", null);

        if (parentId != null) {
            commentClosureRepository.createClosures(savedComment.getCommentId(), parentId);
        } else {
            commentClosureRepository.createSelfClosure(savedComment.getCommentId());
        }

        commentRewardService.rewardCreate(userId, savedComment.getCommentId());
        if (parentComment != null) {
            commentNotificationService.publishReplyNotification(user, agent, parentComment, parentId);
        } else {
            commentNotificationService.publishCreateNotification(user, agent, post, postId);
        }

        return savedComment;
    }

    @Transactional
    public Comment updateComment(Long userId, Long commentId, String content) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        User user = getWritableUser(userId);
        sanctionService.validateNotMuted(user);
        validatePostReadable(comment.getPost(), user);

        if (comment.getIsDeleted()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        if (!comment.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        String originalContent = comment.getContent();
        String sanitizedContent = InputSanitizer.stripHtml(content);
        comment.updateContent(sanitizedContent);

        saveCommentVersion(comment, user, "MODIFY", originalContent);
        return comment;
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        User user = getWritableUser(userId);
        validatePostReadable(comment.getPost(), user);

        if (comment.getIsDeleted()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        if (!comment.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        String originalContent = comment.getContent();
        comment.deleteComment();
        postRepository.decrementCommentCount(comment.getPost().getPostId());

        saveCommentVersion(comment, user, "DELETE", originalContent);
        commentRewardService.rollbackCreateReward(userId, user, commentId);
    }

    @Transactional
    public void likeComment(Long userId, Long commentId) {
        User user = getWritableUser(userId);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        validatePostReadable(comment.getPost(), user);

        if (comment.getIsDeleted()) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        CommentLike commentLike = CommentLike.builder()
                .user(user)
                .comment(comment)
                .build();
        try {
            commentLikeRepository.saveAndFlush(commentLike);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.ALREADY_LIKED);
        }
        commentRepository.incrementLikeCount(commentId);
        commentNotificationService.publishLikeNotification(user, comment, commentId);
    }

    @Transactional
    public void unlikeComment(Long userId, Long commentId) {
        User user = getWritableUser(userId);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        validatePostReadable(comment.getPost(), user);

        int deletedCount = commentLikeRepository.deleteByUserIdAndCommentId(userId, commentId);
        if (deletedCount == 0) {
            throw new BusinessException(ErrorCode.NOT_LIKED);
        }

        commentRepository.decrementLikeCount(commentId);
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

    private User getWritableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        sanctionService.validateNotBanned(user);
        return user;
    }

    private void validatePostReadable(Post post, User viewer) {
        commentPostAccessService.validateReadable(post, commentPostAccessService.resolveReadContext(viewer));
    }
}
