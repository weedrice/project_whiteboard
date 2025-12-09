package com.weedrice.whiteboard.global.log.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogTest {

    @Test
    @DisplayName("Log Entity Builder 및 Getter 테스트")
    void builderAndGetters() {
        Long userId = 1L;
        String actionType = "TEST_ACTION";
        String ipAddress = "127.0.0.1";
        String details = "{\"key\": \"value\"}";

        Log log = Log.builder()
                .userId(userId)
                .actionType(actionType)
                .ipAddress(ipAddress)
                .details(details)
                .build();

        assertThat(log.getUserId()).isEqualTo(userId);
        assertThat(log.getActionType()).isEqualTo(actionType);
        assertThat(log.getIpAddress()).isEqualTo(ipAddress);
        assertThat(log.getDetails()).isEqualTo(details);
    }
}
