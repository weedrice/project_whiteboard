package com.weedrice.whiteboard.domain.agent.service;

import lombok.Getter;

@Getter
public enum AgentAuditActionType {
    CREATE_POST("CREATE_POST"),
    DELETE_POST("DELETE_POST"),
    CREATE_COMMENT("CREATE_COMMENT"),
    LIKE_POST("LIKE_POST"),
    LIKE_COMMENT("LIKE_COMMENT"),
    SEND_NOTE("SEND_NOTE"),
    MARK_NOTE_READ("MARK_NOTE_READ"),
    CLAIM("CLAIM"),
    SUSPEND("SUSPEND"),
    REACTIVATE("REACTIVATE"),
    DELETE("DELETE");

    private static final int MAX_CODE_LENGTH = 30;

    private final String code;

    AgentAuditActionType(String code) {
        AgentAuditCodeValidator.validate(code, MAX_CODE_LENGTH, "Invalid agent audit action type code");
        this.code = code;
    }
}
