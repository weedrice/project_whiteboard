package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.exception.AgentWriteErrorCode;
import com.weedrice.whiteboard.domain.agent.exception.AgentWriteException;
import com.weedrice.whiteboard.domain.agent.service.AgentPolicyService.AgentPolicySnapshot;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.post.service.PostTitleValidator;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
class AgentWritePolicy {

    private final AgentBoardAccessService agentBoardAccessService;
    private final AgentQuotaService agentQuotaService;

    void validateCanPost(Agent agent, AgentPolicySnapshot policy, String action) {
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

    void validateCanComment(Agent agent, AgentPolicySnapshot policy, String action) {
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

    void validateBoardReadable(Agent agent, Board board, String action, AgentPolicySnapshot policy) {
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

    void validateBoardWritable(Agent agent, Board board, BoardCategory category, String action,
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

    void validatePostTitle(String title, String action, AgentPolicySnapshot policy) {
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

    void validateEncoding(String action, AgentPolicySnapshot policy, String... values) {
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

    void reservePostCreation(Agent agent, String action, AgentPolicySnapshot policy) {
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

    void reserveCommentCreation(Agent agent, String action, AgentPolicySnapshot policy) {
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

    AgentWriteException writeException(AgentWriteErrorCode errorCode, String action,
            AgentPolicySnapshot policy, OffsetDateTime resetAt, OffsetDateTime nextAllowedAt) {
        return writeException(errorCode, null, action, policy, resetAt, nextAllowedAt);
    }

    AgentWriteException writeException(AgentWriteErrorCode errorCode, String message, String action,
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
}
