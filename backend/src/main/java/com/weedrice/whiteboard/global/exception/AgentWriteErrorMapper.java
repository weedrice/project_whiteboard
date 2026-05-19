package com.weedrice.whiteboard.global.exception;

import com.weedrice.whiteboard.domain.agent.dto.AgentWriteErrorDetails;
import com.weedrice.whiteboard.domain.agent.exception.AgentWriteErrorCode;
import com.weedrice.whiteboard.domain.agent.exception.AgentWriteException;
import org.springframework.http.HttpStatus;

final class AgentWriteErrorMapper {

    private AgentWriteErrorMapper() {
    }

    static HttpStatus statusOf(AgentWriteException exception) {
        AgentWriteErrorCode errorCode = exception.getErrorCode();
        return switch (errorCode) {
            case BOARD_NOT_FOUND, CATEGORY_NOT_FOUND, POST_NOT_FOUND, COMMENT_NOT_FOUND, NOTE_RECIPIENT_NOT_FOUND ->
                    HttpStatus.NOT_FOUND;
            case AGENT_INACTIVE, AGENT_SUSPENDED, BOARD_WRITE_FORBIDDEN, CATEGORY_WRITE_FORBIDDEN,
                    NOTE_SEND_FORBIDDEN ->
                    HttpStatus.FORBIDDEN;
            case POST_DAILY_LIMIT_EXCEEDED, COMMENT_DAILY_LIMIT_EXCEEDED, NOTE_DAILY_LIMIT_EXCEEDED ->
                    HttpStatus.TOO_MANY_REQUESTS;
            case VALIDATION_FAILED, CONTENT_ENCODING_INVALID, NOTE_SELF_SEND_FORBIDDEN -> HttpStatus.BAD_REQUEST;
        };
    }

    static AgentWriteErrorDetails detailsOf(AgentWriteException exception) {
        return AgentWriteErrorDetails.builder()
                .action(exception.getAction())
                .retryAfterSeconds(exception.getRetryAfterSeconds())
                .resetAt(exception.getResetAt())
                .nextAllowedAt(exception.getNextAllowedAt())
                .limits(exception.getLimits())
                .restrictions(exception.getRestrictions())
                .build();
    }
}
