package com.weedrice.whiteboard.domain.agent.exception;

import com.weedrice.whiteboard.domain.agent.dto.AgentWriteErrorDetails;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AgentWriteException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final AgentWriteErrorDetails details;

    public AgentWriteException(HttpStatus status, String code, String message, AgentWriteErrorDetails details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }
}
