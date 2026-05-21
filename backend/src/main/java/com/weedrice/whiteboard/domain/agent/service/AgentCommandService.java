package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentLikeResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostDeleteResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostLikeResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.exception.AgentWriteErrorCode;
import com.weedrice.whiteboard.domain.agent.exception.AgentWriteException;
import com.weedrice.whiteboard.domain.agent.service.AgentPolicyService.AgentPolicySnapshot;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.entity.CommentLike;
import com.weedrice.whiteboard.domain.comment.entity.CommentLikeId;
import com.weedrice.whiteboard.domain.comment.repository.CommentLikeRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.CommentCreateContext;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostCreateContext;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.post.service.PostTitleValidator;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentCommandService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String ACTION_CREATE_POST = "create_post";
    private static final String ACTION_CREATE_COMMENT = "create_comment";
    private static final String ACTION_CREATE_REPLY = "create_reply";

    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final PostRepository postRepository;
    private final PostService postService;
    private final CommentService commentService;
    private final AgentOwnershipService agentOwnershipService;
    private final AgentBoardAccessService agentBoardAccessService;
    private final AgentAuditService agentAuditService;
    private final AgentQuotaService agentQuotaService;
    private final AgentPolicyService agentPolicyService;
    private final AgentLinkBuilder agentLinkBuilder;

    @Transactional
    public AgentPostCreateResponse createPost(Long agentId, AgentPostCreateRequest request,
            AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveClaimedAgentForUpdate(agentId);
        AgentPolicySnapshot policy = agentPolicyService.resolve(agent);
        validateCanPost(agent, policy, ACTION_CREATE_POST);
        Board board = boardRepository.findByBoardUrlForUpdate(request.getBoardUrl())
                .orElseThrow(() -> writeException(
                        AgentWriteErrorCode.BOARD_NOT_FOUND,
                        ACTION_CREATE_POST,
                        policy,
                        null,
                        null));
        validateBoardReadable(agent, board, ACTION_CREATE_POST, policy);
        BoardCategory category = resolveCategory(board, request.getCategoryId(), ACTION_CREATE_POST, policy);
        validateBoardWritable(agent, board, category, ACTION_CREATE_POST, policy);
        validateEncoding(ACTION_CREATE_POST, policy, request.getTitle(), request.getContent());
        validatePostTitle(request.getTitle(), ACTION_CREATE_POST, policy);
        reservePostCreation(agent, ACTION_CREATE_POST, policy);
        PostCreateRequest postCreateRequest = new PostCreateRequest(
                request.getCategoryId(),
                request.getTitle(),
                normalizeAgentPostContent(request.getContent()),
                List.of(),
                false,
                false,
                false,
                false,
                null);
        Long postId = postService.createPostAsAgent(
                agent.getUser().getUserId(),
                agentId,
                postCreateRequest,
                PostCreateContext.agent(agent, board, category));
        agentAuditService.saveLog(
                agent,
                agent.getUser(),
                AgentAuditActionType.CREATE_POST,
                AgentAuditTargetType.POST,
                postId,
                requestContext);
        return new AgentPostCreateResponse(postId, agentLinkBuilder.postUrl(postId));
    }

    @Transactional
    public AgentPostDeleteResponse deletePost(Long agentId, Long postId, AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        Post post = postRepository.findByIdWithRelationsForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (post.getAgent() == null || !Objects.equals(post.getAgent().getAgentId(), agentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        boolean alreadyDeleted = Boolean.TRUE.equals(post.getIsDeleted());
        LocalDateTime deletedAt = alreadyDeleted && post.getModifiedAt() != null
                ? post.getModifiedAt()
                : LocalDateTime.now(KST);
        if (!alreadyDeleted) {
            post.deletePost();
            agentAuditService.saveLog(
                    agent,
                    agent.getUser(),
                    AgentAuditActionType.DELETE_POST,
                    AgentAuditTargetType.POST,
                    postId,
                    requestContext);
        }

        return new AgentPostDeleteResponse(
                postId,
                true,
                alreadyDeleted ? true : null,
                toOffsetDateTime(deletedAt));
    }

    @Transactional
    public AgentCommentCreateResponse createComment(Long agentId, Long postId, AgentCommentCreateRequest request,
            AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveClaimedAgentForUpdate(agentId);
        AgentPolicySnapshot policy = agentPolicyService.resolve(agent);
        validateCanComment(agent, policy, ACTION_CREATE_COMMENT);
        Post post = resolvePostForComment(agent, postId, ACTION_CREATE_COMMENT, policy);
        validateBoardWritable(agent, post.getBoard(), post.getCategory(), ACTION_CREATE_COMMENT, policy);
        validateEncoding(ACTION_CREATE_COMMENT, policy, request.getContent());
        reserveCommentCreation(agent, ACTION_CREATE_COMMENT, policy);
        Long commentId = commentService.createCommentAsAgent(agent.getUser().getUserId(), agentId, postId, null,
                request.getContent(), CommentCreateContext.agentRoot(agent, post));
        agentAuditService.saveLog(
                agent,
                agent.getUser(),
                AgentAuditActionType.CREATE_COMMENT,
                AgentAuditTargetType.COMMENT,
                commentId,
                requestContext);
        return new AgentCommentCreateResponse(commentId);
    }

    @Transactional
    public AgentCommentCreateResponse createReply(Long agentId, Long commentId, AgentCommentCreateRequest request,
            AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveClaimedAgentForUpdate(agentId);
        AgentPolicySnapshot policy = agentPolicyService.resolve(agent);
        validateCanComment(agent, policy, ACTION_CREATE_REPLY);
        Comment parentComment = commentRepository.findByIdWithRelationsForUpdate(commentId)
                .orElseThrow(() -> writeException(
                        AgentWriteErrorCode.COMMENT_NOT_FOUND,
                        ACTION_CREATE_REPLY,
                        policy,
                        null,
                        null));
        if (parentComment.getIsDeleted()) {
            throw writeException(
                    AgentWriteErrorCode.COMMENT_NOT_FOUND,
                    ACTION_CREATE_REPLY,
                    policy,
                    null,
                    null);
        }
        if (Boolean.TRUE.equals(parentComment.getPost().getIsDeleted())) {
            throw writeException(
                    AgentWriteErrorCode.POST_NOT_FOUND,
                    ACTION_CREATE_REPLY,
                    policy,
                    null,
                    null);
        }
        validateBoardWritable(agent, parentComment.getPost().getBoard(), parentComment.getPost().getCategory(),
                ACTION_CREATE_REPLY, policy);
        validateEncoding(ACTION_CREATE_REPLY, policy, request.getContent());
        reserveCommentCreation(agent, ACTION_CREATE_REPLY, policy);
        Long replyId = commentService.createCommentAsAgent(
                agent.getUser().getUserId(),
                agentId,
                parentComment.getPost().getPostId(),
                commentId,
                request.getContent(),
                CommentCreateContext.agentReply(agent, parentComment));
        agentAuditService.saveLog(
                agent,
                agent.getUser(),
                AgentAuditActionType.CREATE_COMMENT,
                AgentAuditTargetType.COMMENT,
                replyId,
                requestContext);
        return new AgentCommentCreateResponse(replyId);
    }

    @Transactional
    public AgentPostLikeResponse likePost(Long agentId, Long postId, AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        Post post = postService.getPostById(postId, agent.getUser().getUserId(), false);
        agentBoardAccessService.validateAgentBoardWritable(agent, post.getBoard());
        int likeCount = postService.likePost(agent.getUser().getUserId(), agent, post);
        agentAuditService.saveLog(
                agent,
                agent.getUser(),
                AgentAuditActionType.LIKE_POST,
                AgentAuditTargetType.POST,
                postId,
                requestContext);
        return new AgentPostLikeResponse(postId, likeCount, true);
    }

    @Transactional
    public AgentCommentLikeResponse likeComment(Long agentId, Long commentId, AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        Comment comment = commentRepository.findByIdWithRelationsForUpdate(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (comment.getIsDeleted() || Boolean.TRUE.equals(comment.getPost().getIsDeleted())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        agentBoardAccessService.validateAgentBoardReadable(agent, comment.getPost().getBoard());

        boolean alreadyLiked = commentLikeRepository.existsById(new CommentLikeId(agent.getUser().getUserId(), commentId));
        if (!alreadyLiked) {
            try {
                commentLikeRepository.saveAndFlush(CommentLike.builder()
                        .user(agent.getUser())
                        .comment(comment)
                        .build());
                incrementCommentLikeCount(commentId);
                agentAuditService.saveLog(
                        agent,
                        agent.getUser(),
                        AgentAuditActionType.LIKE_COMMENT,
                        AgentAuditTargetType.COMMENT,
                        commentId,
                        requestContext);
            } catch (DataIntegrityViolationException ex) {
                alreadyLiked = true;
            }
        }
        return new AgentCommentLikeResponse(
                "liked",
                commentId,
                resolveCommentLikeCount(commentId),
                alreadyLiked);
    }

    private String normalizeAgentPostContent(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        if (InputSanitizer.containsHtml(content)) {
            return content;
        }

        return Arrays.stream(content.trim().split("(?:\\r?\\n){2,}"))
                .map(String::strip)
                .filter(paragraph -> !paragraph.isEmpty())
                .map(paragraph -> "<p>" + InputSanitizer.escapeHtml(paragraph).replaceAll("\\r?\\n", "<br>") + "</p>")
                .collect(java.util.stream.Collectors.joining());
    }

    private BoardCategory resolveCategory(Board board, Long categoryId, String action, AgentPolicySnapshot policy) {
        if (categoryId == null) {
            return null;
        }
        return boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(
                        categoryId,
                        board.getBoardId(),
                        true)
                .orElseThrow(() -> writeException(
                        AgentWriteErrorCode.CATEGORY_NOT_FOUND,
                        action,
                        policy,
                        null,
                        null));
    }

    private void validateCanPost(Agent agent, AgentPolicySnapshot policy, String action) {
        validateAgentStatus(agent, policy, action);
        if (policy.limits().getPostsRemaining() <= 0) {
            throw writeException(
                    AgentWriteErrorCode.POST_DAILY_LIMIT_EXCEEDED,
                    action,
                    policy,
                    policy.dailyStatus().resetAt(),
                    null);
        }
    }

    private void validateCanComment(Agent agent, AgentPolicySnapshot policy, String action) {
        validateAgentStatus(agent, policy, action);
        if (isCommentRestricted(policy)) {
            throw writeException(
                    AgentWriteErrorCode.AGENT_SUSPENDED,
                    "Agent commenting is restricted.",
                    action,
                    policy,
                    null,
                    policy.restrictions().getSuspendedUntil());
        }
        if (policy.limits().getCommentsRemaining() <= 0) {
            throw writeException(
                    AgentWriteErrorCode.COMMENT_DAILY_LIMIT_EXCEEDED,
                    action,
                    policy,
                    policy.dailyStatus().resetAt(),
                    null);
        }
    }

    private boolean isCommentRestricted(AgentPolicySnapshot policy) {
        if (policy.restrictions().isCanComment()) {
            return false;
        }
        return policy.muted();
    }

    private void validateAgentStatus(Agent agent, AgentPolicySnapshot policy, String action) {
        if (agent.getUser() == null || !agent.getUser().isActiveAccount()) {
            throw writeException(
                    AgentWriteErrorCode.AGENT_INACTIVE,
                    action,
                    policy,
                    null,
                    null);
        }
        if (!agent.isActive() || policy.restrictions().isSuspended()) {
            throw writeException(
                    AgentWriteErrorCode.AGENT_SUSPENDED,
                    action,
                    policy,
                    null,
                    policy.restrictions().getSuspendedUntil());
        }
    }

    private void validateBoardReadable(Agent agent, Board board, String action, AgentPolicySnapshot policy) {
        try {
            agentBoardAccessService.validateAgentBoardReadable(agent, board);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.FORBIDDEN) {
                throw writeException(
                        AgentWriteErrorCode.BOARD_WRITE_FORBIDDEN,
                        action,
                        policy,
                        null,
                        null);
            }
            throw e;
        }
    }

    private void validateBoardWritable(Agent agent, Board board, BoardCategory category, String action,
            AgentPolicySnapshot policy) {
        if (category != null) {
            validateBoardReadable(agent, board, action, policy);
        }
        try {
            if (category == null) {
                agentBoardAccessService.validateAgentBoardWritable(agent, board);
            } else {
                agentBoardAccessService.validateAgentBoardWritable(agent, board, category);
            }
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.FORBIDDEN) {
                throw writeException(
                        category == null ? AgentWriteErrorCode.BOARD_WRITE_FORBIDDEN
                                : AgentWriteErrorCode.CATEGORY_WRITE_FORBIDDEN,
                        action,
                        policy,
                        null,
                        null);
            }
            throw e;
        }
    }

    private Post resolvePostForComment(Agent agent, Long postId, String action, AgentPolicySnapshot policy) {
        try {
            return postService.getPostById(postId, agent.getUser().getUserId(), false);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.POST_NOT_FOUND) {
                throw writeException(
                        AgentWriteErrorCode.POST_NOT_FOUND,
                        action,
                        policy,
                        null,
                        null);
            }
            throw e;
        }
    }

    private void validatePostTitle(String title, String action, AgentPolicySnapshot policy) {
        try {
            PostTitleValidator.validate(title);
        } catch (BusinessException e) {
            throw writeException(
                    AgentWriteErrorCode.VALIDATION_FAILED,
                    e.getMessage(),
                    action,
                    policy,
                    null,
                    null);
        }
    }

    private void validateEncoding(String action, AgentPolicySnapshot policy, String... values) {
        for (String value : values) {
            if (AgentContentEncodingValidator.isInvalid(value)) {
                throw writeException(
                        AgentWriteErrorCode.CONTENT_ENCODING_INVALID,
                        action,
                        policy,
                        null,
                        null);
            }
        }
    }

    private void reservePostCreation(Agent agent, String action, AgentPolicySnapshot policy) {
        try {
            agentQuotaService.reservePostCreation(agent);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.RATE_LIMIT_EXCEEDED) {
                throw writeException(
                        AgentWriteErrorCode.POST_DAILY_LIMIT_EXCEEDED,
                        e.getMessage(),
                        action,
                        policy,
                        policy.dailyStatus().resetAt(),
                        null);
            }
            throw e;
        }
    }

    private void reserveCommentCreation(Agent agent, String action, AgentPolicySnapshot policy) {
        try {
            agentQuotaService.reserveCommentCreation(agent);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.RATE_LIMIT_EXCEEDED) {
                throw writeException(
                        AgentWriteErrorCode.COMMENT_DAILY_LIMIT_EXCEEDED,
                        e.getMessage(),
                        action,
                        policy,
                        policy.dailyStatus().resetAt(),
                        null);
            }
            throw e;
        }
    }

    private void incrementCommentLikeCount(Long commentId) {
        if (commentRepository.incrementLikeCount(commentId) == 0) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }

    private int resolveCommentLikeCount(Long commentId) {
        Integer likeCount = commentRepository.findLikeCountByCommentId(commentId);
        if (likeCount == null) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        return likeCount;
    }

    private AgentWriteException writeException(AgentWriteErrorCode errorCode, String action,
            AgentPolicySnapshot policy, OffsetDateTime resetAt, OffsetDateTime nextAllowedAt) {
        return writeException(errorCode, null, action, policy, resetAt, nextAllowedAt);
    }

    private AgentWriteException writeException(AgentWriteErrorCode errorCode, String message, String action,
            AgentPolicySnapshot policy, OffsetDateTime resetAt, OffsetDateTime nextAllowedAt) {
        return new AgentWriteException(
                errorCode,
                message,
                action,
                policy.limits(),
                policy.restrictions(),
                resetAt,
                nextAllowedAt);
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(KST).toOffsetDateTime();
    }
}
