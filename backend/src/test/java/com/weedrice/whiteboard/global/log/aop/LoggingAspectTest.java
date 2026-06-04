package com.weedrice.whiteboard.global.log.aop;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingAspectTest {

    @Test
    void requestUriForLog_returnsUriWhenQueryStringIsMissing() {
        assertThat(LoggingAspect.requestUriForLog("/api/v1/posts", null))
                .isEqualTo("/api/v1/posts");
        assertThat(LoggingAspect.requestUriForLog("/api/v1/posts", " "))
                .isEqualTo("/api/v1/posts");
    }

    @Test
    void requestUriForLog_omitsQueryString() {
        String uri = LoggingAspect.requestUriForLog(
                "/api/v1/auth/callback",
                "token=abc123&keyword=visible&password=secret");

        assertThat(uri).isEqualTo("/api/v1/auth/callback");
    }
}
