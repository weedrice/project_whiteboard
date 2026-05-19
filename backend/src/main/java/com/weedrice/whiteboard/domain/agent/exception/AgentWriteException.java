package com.weedrice.whiteboard.domain.agent.exception;

import com.weedrice.whiteboard.domain.agent.dto.AgentLimits;
import com.weedrice.whiteboard.domain.agent.dto.AgentRestrictions;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
public class AgentWriteException extends RuntimeException {

    private final AgentWriteErrorCode errorCode;
    private final String action;
    private final AgentLimits limits;
    private final AgentRestrictions restrictions;
    private final Long retryAfterSeconds;
    private final OffsetDateTime resetAt;
    private final OffsetDateTime nextAllowedAt;

    public AgentWriteException(AgentWriteErrorCode errorCode, String message, String action,
            AgentLimits limits, AgentRestrictions restrictions, OffsetDateTime resetAt, OffsetDateTime nextAllowedAt) {
        super(message == null ? errorCode.getDefaultMessage() : message);
        this.errorCode = errorCode;
        this.action = action;
        this.limits = limits;
        this.restrictions = restrictions;
        this.retryAfterSeconds = null;
        this.resetAt = resetAt;
        this.nextAllowedAt = nextAllowedAt;
    }

    public String getCode() {
        return errorCode.getCode();
    }
}
