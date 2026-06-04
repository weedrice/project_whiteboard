package com.weedrice.whiteboard.domain.agent.service;

final class AgentAuditCodeValidator {

    private AgentAuditCodeValidator() {
    }

    static void validate(String code, int maxLength, String errorMessage) {
        if (code == null || code.isBlank() || code.length() > maxLength) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
}
