package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentCreateResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentCommentLikeResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostCreateResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostDeleteResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentPostLikeResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.port.AgentContentPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentCommandService {

    private final AgentContentPort agentContentPort;
    private final AgentOwnershipService agentOwnershipService;
    private final AgentAuditService agentAuditService;
    private final AgentLinkBuilder agentLinkBuilder;
    private final AgentWriteAuditRecorder agentWriteAuditRecorder;

    @Transactional
    public AgentPostCreateResponse createPost(Long agentId, AgentPostCreateRequest request,
            AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveClaimedAgentForUpdate(agentId);
        Long postId = agentContentPort.createPost(agent, request);
        agentWriteAuditRecorder.recordPostCreated(agent, postId, requestContext);
        return new AgentPostCreateResponse(postId, agentLinkBuilder.postUrl(postId));
    }

    @Transactional
    public AgentPostDeleteResponse deletePost(Long agentId, Long postId, AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        AgentContentPort.DeletePostResult result = agentContentPort.deletePost(agent, postId);
        if (!result.alreadyDeleted()) {
            agentAuditService.saveLog(
                    agent,
                    agent.getUserId(),
                    AgentAuditActionType.DELETE_POST,
                    AgentAuditTargetType.POST,
                    postId,
                    requestContext);
        }

        return new AgentPostDeleteResponse(
                postId,
                true,
                result.alreadyDeleted() ? true : null,
                AgentDateTimes.toOffsetDateTime(result.deletedAt()));
    }

    @Transactional
    public AgentCommentCreateResponse createComment(Long agentId, Long postId, AgentCommentCreateRequest request,
            AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveClaimedAgentForUpdate(agentId);
        Long commentId = agentContentPort.createComment(agent, postId, request);
        agentWriteAuditRecorder.recordCommentCreated(agent, commentId, requestContext);
        return new AgentCommentCreateResponse(commentId);
    }

    @Transactional
    public AgentCommentCreateResponse createReply(Long agentId, Long commentId, AgentCommentCreateRequest request,
            AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveClaimedAgentForUpdate(agentId);
        Long replyId = agentContentPort.createReply(agent, commentId, request);
        agentWriteAuditRecorder.recordCommentCreated(agent, replyId, requestContext);
        return new AgentCommentCreateResponse(replyId);
    }

    @Transactional
    public AgentPostLikeResponse likePost(Long agentId, Long postId, AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        AgentContentPort.LikeResult result = agentContentPort.likePost(agent, postId);
        agentAuditService.saveLog(
                agent,
                agent.getUserId(),
                AgentAuditActionType.LIKE_POST,
                AgentAuditTargetType.POST,
                postId,
                requestContext);
        return new AgentPostLikeResponse(postId, result.likeCount(), true);
    }

    @Transactional
    public AgentCommentLikeResponse likeComment(Long agentId, Long commentId, AgentRequestContext requestContext) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        AgentContentPort.LikeResult result = agentContentPort.likeComment(agent, commentId);
        if (!result.alreadyLiked()) {
            agentAuditService.saveLog(
                    agent,
                    agent.getUserId(),
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
