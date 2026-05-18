package com.weedrice.whiteboard.domain.agent.service;

import lombok.Getter;

@Getter
public enum AgentAuditActionType {
    CREATE_POST("CREATE_POST"),
    DELETE_POST("DELETE_POST"),
    CREATE_COMMENT("CREATE_COMMENT"),
    LIKE_POST("LIKE_POST"),
    CLAIM("CLAIM"),
    SUSPEND("SUSPEND"),
    REACTIVATE("REACTIVATE"),
    DELETE("DELETE");

    private static final int MAX_CODE_LENGTH = 30;

    private final String code;

    AgentAuditActionType(String code) {
        validateCode(code, MAX_CODE_LENGTH);
        this.code = code;
    }

    private static void validateCode(String code, int maxLength) {
        if (code == null || code.isBlank() || code.length() > maxLength) {
            throw new IllegalArgumentException("Invalid agent audit action type code");
        }
    }
}
