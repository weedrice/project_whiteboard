package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentLikeResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostDeleteResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostLikeResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentPolicyService.AgentPolicySnapshot;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.CommentCreateContext;
import com.weedrice.whiteboard.domain.comment.service.CommentLikeCommand;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostCommandService;
import com.weedrice.whiteboard.domain.post.service.PostCreateContext;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentCommandService {

    private static final String ACTION_CREATE_POST = "create_post";
    private static final String ACTION_CREATE_COMMENT = "create_comment";
    private static final String ACTION_CREATE_REPLY = "create_reply";

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final PostService postService;
    private final PostCommandService postCommandService;
    private final CommentService commentService;
    private final CommentLikeCommand commentLikeCommand;
    private final AgentOwnershipService agentOwnershipService;
    private final AgentBoardAccessService agentBoardAccessService;
    private final AgentCommentAccessService agentCommentAccessService;
    private final AgentAuditService agentAuditService;
    private final AgentPolicyService agentPolicyService;
    private final AgentLinkBuilder agentLinkBuilder;
    private final AgentWritePolicy agentWritePolicy;
    private final AgentWriteTargetResolver agentWriteTargetResolver;
    private final AgentWriteRequestMapper agentWriteRequestMapper;
    private final AgentWriteAuditRecorder agentWriteAuditRecorder;
    private final Clock clock;

    @Transactional
    public AgentPostCreateResponse createPost(Long agentId, AgentPostCreateRequest request,
            AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveClaimedAgentForUpdate(agentId);
        AgentPolicySnapshot policy = agentPolicyService.resolve(agent);
        agentWritePolicy.validateCanPost(agent, policy, ACTION_CREATE_POST);
        Board board = agentWriteTargetResolver.resolveBoardForPost(request.getBoardUrl(), ACTION_CREATE_POST, policy);
        agentWritePolicy.validateBoardReadable(agent, board, ACTION_CREATE_POST, policy);
        BoardCategory category = agentWriteTargetResolver.resolveCategory(
                board,
                request.getCategoryId(),
                ACTION_CREATE_POST,
                policy);
        agentWritePolicy.validateBoardWritable(agent, board, category, ACTION_CREATE_POST, policy);
        agentWritePolicy.validateEncoding(ACTION_CREATE_POST, policy, request.getTitle(), request.getContent());
        agentWritePolicy.validatePostTitle(request.getTitle(), ACTION_CREATE_POST, policy);
        agentWritePolicy.reservePostCreation(agent, ACTION_CREATE_POST, policy);
        Long postId = postService.createPostAsAgent(
                agent.getUser().getUserId(),
                agentId,
                agentWriteRequestMapper.toPostCreateRequest(request),
                PostCreateContext.agent(agent, board, category));
        agentWriteAuditRecorder.recordPostCreated(agent, postId, requestContext);
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
                : AgentDateTimes.now(clock);
        if (!alreadyDeleted) {
            postCommandService.deleteAgentOwnedPost(post, agentId, agent.getUser());
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
                AgentDateTimes.toOffsetDateTime(deletedAt));
    }

    @Transactional
    public AgentCommentCreateResponse createComment(Long agentId, Long postId, AgentCommentCreateRequest request,
            AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveClaimedAgentForUpdate(agentId);
        AgentPolicySnapshot policy = agentPolicyService.resolve(agent);
        agentWritePolicy.validateCanComment(agent, policy, ACTION_CREATE_COMMENT);
        Post post = agentWriteTargetResolver.resolvePostForComment(agent, postId, ACTION_CREATE_COMMENT, policy);
        agentWritePolicy.validateBoardWritable(agent, post.getBoard(), post.getCategory(),
                ACTION_CREATE_COMMENT, policy);
        agentWritePolicy.validateEncoding(ACTION_CREATE_COMMENT, policy, request.getContent());
        agentWritePolicy.reserveCommentCreation(agent, ACTION_CREATE_COMMENT, policy);
        Long commentId = commentService.createCommentAsAgent(agent.getUser().getUserId(), agentId, postId, null,
                request.getContent(), CommentCreateContext.agentRoot(agent, post));
        agentWriteAuditRecorder.recordCommentCreated(agent, commentId, requestContext);
        return new AgentCommentCreateResponse(commentId);
    }

    @Transactional
    public AgentCommentCreateResponse createReply(Long agentId, Long commentId, AgentCommentCreateRequest request,
            AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveClaimedAgentForUpdate(agentId);
        commentService.lockAuthorForWrite(agent.getUser().getUserId());
        AgentPolicySnapshot policy = agentPolicyService.resolve(agent);
        agentWritePolicy.validateCanComment(agent, policy, ACTION_CREATE_REPLY);
        Comment parentComment = agentWriteTargetResolver.resolveParentCommentForReply(
                commentId,
                ACTION_CREATE_REPLY,
                policy);
        agentWritePolicy.validateBoardWritable(agent, parentComment.getPost().getBoard(),
                parentComment.getPost().getCategory(),
                ACTION_CREATE_REPLY, policy);
        agentWritePolicy.validateEncoding(ACTION_CREATE_REPLY, policy, request.getContent());
        agentWritePolicy.reserveCommentCreation(agent, ACTION_CREATE_REPLY, policy);
        Long replyId = commentService.createCommentAsAgent(
                agent.getUser().getUserId(),
                agentId,
                parentComment.getPost().getPostId(),
                commentId,
                request.getContent(),
                CommentCreateContext.agentReply(agent, parentComment));
        agentWriteAuditRecorder.recordCommentCreated(agent, replyId, requestContext);
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
        agentCommentAccessService.validateReadableActiveComment(agent, comment);

        CommentLikeCommand.CommentLikeResult result = commentLikeCommand.like(
                agent.getUser(),
                comment,
                CommentLikeCommand.DuplicatePolicy.RETURN_ALREADY_LIKED);
        if (!result.alreadyLiked()) {
            agentAuditService.saveLog(
                    agent,
                    agent.getUser(),
                    AgentAuditActionType.LIKE_COMMENT,
                    AgentAuditTargetType.COMMENT,
                    commentId,
                    requestContext);
        }
        return new AgentCommentLikeResponse(
                "liked",
                commentId,
                result.likeCount(),
                result.alreadyLiked());
    }

}
