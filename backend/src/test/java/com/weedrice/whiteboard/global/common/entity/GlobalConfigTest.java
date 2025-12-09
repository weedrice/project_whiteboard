package com.weedrice.whiteboard.global.common.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalConfigTest {

    @Test
    @DisplayName("GlobalConfig 생성 및 필드 확인")
    void createAndGetters() {
        GlobalConfig config = new GlobalConfig("testKey", "testValue", "Test Description");

        assertThat(config.getConfigKey()).isEqualTo("testKey");
        assertThat(config.getConfigValue()).isEqualTo("testValue");
        assertThat(config.getDescription()).isEqualTo("Test Description");
    }

    @Test
    @DisplayName("setConfigValue 메서드 확인")
    void setConfigValue() {
        GlobalConfig config = new GlobalConfig("testKey", "testValue", "Test Description");
        config.setConfigValue("newValue");

        assertThat(config.getConfigValue()).isEqualTo("newValue");
    }
}
