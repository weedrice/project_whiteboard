package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.exception.AgentWriteErrorCode;
import com.weedrice.whiteboard.domain.agent.exception.AgentWriteException;
import com.weedrice.whiteboard.domain.agent.service.AgentPolicyService.AgentPolicySnapshot;
import com.weedrice.whiteboard.domain.message.constant.MessageConstraints;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.util.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
class AgentNoteSendPolicy {

    static final String ACTION_SEND_NOTE = "send_note";

    private final UserBlockService userBlockService;

    public void validateCanSendNote(AgentPolicySnapshot policy) {
        if (policy.restrictions().isSuspended()) {
            throw writeException(
                    AgentWriteErrorCode.AGENT_SUSPENDED,
                    policy,
                    null,
                    policy.restrictions().getSuspendedUntil());
        }
        if (!policy.restrictions().isCanSendNote()) {
            throw writeException(
                    AgentWriteErrorCode.NOTE_SEND_FORBIDDEN,
                    policy,
                    null,
                    null);
        }
        if (policy.limits().getNotesRemaining() <= 0) {
            throw writeException(
                    AgentWriteErrorCode.NOTE_DAILY_LIMIT_EXCEEDED,
                    policy,
                    policy.dailyStatus().resetAt(),
                    null);
        }
    }

    public void validateRecipient(Agent sender, Agent recipient, AgentPolicySnapshot policy) {
        if (Objects.equals(sender.getAgentId(), recipient.getAgentId())) {
            throw writeException(
                    AgentWriteErrorCode.NOTE_SELF_SEND_FORBIDDEN,
                    policy,
                    null,
                    null);
        }
        if (!recipient.isActive() || recipient.getUser() == null || !recipient.getUser().isActiveAccount()) {
            throw writeException(
                    AgentWriteErrorCode.NOTE_SEND_FORBIDDEN,
                    policy,
                    null,
                    null);
        }
    }

    public void validateNotBlocked(Agent sender, Agent recipient, AgentPolicySnapshot policy) {
        User senderUser = sender.getUser();
        User recipientUser = recipient.getUser();
        if (userBlockService.isEitherDirectionBlocked(senderUser.getUserId(), recipientUser.getUserId())) {
            throw writeException(
                    AgentWriteErrorCode.NOTE_SEND_FORBIDDEN,
                    policy,
                    null,
                    null);
        }
    }

    public String normalizeContent(String value, AgentPolicySnapshot policy) {
        String normalized = value == null ? null : InputSanitizer.stripHtml(value).trim();
        if (normalized == null || normalized.isBlank() || normalized.length() > MessageConstraints.MAX_CONTENT_LENGTH) {
            throw writeException(
                    AgentWriteErrorCode.VALIDATION_FAILED,
                    policy,
                    null,
                    null);
        }
        return normalized;
    }

    public AgentWriteException recipientNotFound(AgentPolicySnapshot policy) {
        return writeException(
                AgentWriteErrorCode.NOTE_RECIPIENT_NOT_FOUND,
                policy,
                null,
                null);
    }

    public AgentWriteException noteDailyLimitExceeded(String message, AgentPolicySnapshot policy) {
        return writeException(
                AgentWriteErrorCode.NOTE_DAILY_LIMIT_EXCEEDED,
                message,
                policy,
                policy.dailyStatus().resetAt(),
                null);
    }

    private AgentWriteException writeException(AgentWriteErrorCode errorCode, AgentPolicySnapshot policy,
            OffsetDateTime resetAt, OffsetDateTime nextAllowedAt) {
        return writeException(errorCode, null, policy, resetAt, nextAllowedAt);
    }

    private AgentWriteException writeException(AgentWriteErrorCode errorCode, String message,
            AgentPolicySnapshot policy, OffsetDateTime resetAt, OffsetDateTime nextAllowedAt) {
        return new AgentWriteException(
                errorCode,
                message,
                ACTION_SEND_NOTE,
                policy.limits(),
                policy.restrictions(),
                resetAt,
                nextAllowedAt);
    }
}
