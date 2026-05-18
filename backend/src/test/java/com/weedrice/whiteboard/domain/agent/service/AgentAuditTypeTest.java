package com.weedrice.whiteboard.domain.agent.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentAuditTypeTest {

    @Test
    void actionTypeCodesMatchPersistedValuesAndFitColumnLimit() {
        assertThat(AgentAuditActionType.values())
                .extracting(AgentAuditActionType::getCode)
                .containsExactly(
                        "CREATE_POST",
                        "DELETE_POST",
                        "CREATE_COMMENT",
                        "LIKE_POST",
                        "CLAIM",
                        "SUSPEND",
                        "REACTIVATE",
                        "DELETE")
                .allSatisfy(code -> {
                    assertThat(code).isNotBlank();
                    assertThat(code).hasSizeLessThanOrEqualTo(30);
                });
    }

    @Test
    void targetTypeCodesMatchPersistedValuesAndFitColumnLimit() {
        assertThat(AgentAuditTargetType.values())
                .extracting(AgentAuditTargetType::getCode)
                .containsExactly("POST", "COMMENT", "AGENT")
                .allSatisfy(code -> {
                    assertThat(code).isNotBlank();
                    assertThat(code).hasSizeLessThanOrEqualTo(20);
                });
    }
}
