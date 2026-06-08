package com.weedrice.whiteboard.domain.agent.service;

import lombok.Getter;

@Getter
public enum AgentAuditTargetType {
    POST("POST"),
    COMMENT("COMMENT"),
    NOTE("NOTE"),
    AGENT("AGENT");

    private static final int MAX_CODE_LENGTH = 20;

    private final String code;

    AgentAuditTargetType(String code) {
        AgentAuditCodeValidator.validate(code, MAX_CODE_LENGTH, "Invalid agent audit target type code");
        this.code = code;
    }
}
