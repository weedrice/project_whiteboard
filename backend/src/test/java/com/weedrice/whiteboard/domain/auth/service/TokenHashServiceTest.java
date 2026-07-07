package com.weedrice.whiteboard.domain.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHashServiceTest {

    private final TokenHashService tokenHashService = new TokenHashService();

    @Test
    @DisplayName("SHA-256 token hash는 UTF-8 기준으로 계산한다")
    void hashSha256_usesUtf8ForNonAsciiToken() {
        String hashedToken = tokenHashService.hashSha256("토큰");

        assertThat(hashedToken)
                .isEqualTo("eb540501fb3127bf2a14e8872361b25a6b1efba30b2fdacdb8f3dbbb3c6b3ce9");
    }
}
