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
        validateCode(code, MAX_CODE_LENGTH);
        this.code = code;
    }

    private static void validateCode(String code, int maxLength) {
        if (code == null || code.isBlank() || code.length() > maxLength) {
            throw new IllegalArgumentException("Invalid agent audit target type code");
        }
    }
}
