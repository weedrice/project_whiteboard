package com.weedrice.whiteboard.global.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientMetadataNormalizerTest {

    @Test
    @DisplayName("IP가 없으면 unknown으로 정규화한다")
    void normalizeIpAddress_blankToUnknown() {
        assertThat(ClientMetadataNormalizer.normalizeIpAddress(null)).isEqualTo("unknown");
        assertThat(ClientMetadataNormalizer.normalizeIpAddress("   ")).isEqualTo("unknown");
    }

    @Test
    @DisplayName("IP 앞뒤 공백을 제거한다")
    void normalizeIpAddress_trimsValue() {
        assertThat(ClientMetadataNormalizer.normalizeIpAddress(" 127.0.0.1 "))
                .isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("프록시 체인은 첫 번째 주소만 사용한다")
    void normalizeIpAddress_selectsFirstForwardedAddress() {
        assertThat(ClientMetadataNormalizer.normalizeIpAddress(" 203.0.113.10, 10.0.0.1 "))
                .isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("프록시 체인의 첫 주소가 비어 있으면 unknown으로 정규화한다")
    void normalizeIpAddress_emptyFirstForwardedAddressToUnknown() {
        assertThat(ClientMetadataNormalizer.normalizeIpAddress(" , 10.0.0.1"))
                .isEqualTo("unknown");
    }

    @Test
    @DisplayName("정규화 결과가 45자를 넘으면 잘라낸다")
    void normalizeIpAddress_truncatesLongValue() {
        assertThat(ClientMetadataNormalizer.normalizeIpAddress("1".repeat(46)))
                .hasSize(45);
    }
}
