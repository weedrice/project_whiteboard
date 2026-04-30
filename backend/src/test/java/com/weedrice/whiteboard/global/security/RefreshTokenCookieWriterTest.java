package com.weedrice.whiteboard.global.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCookieWriterTest {

    private final RefreshTokenCookieWriter writer = new RefreshTokenCookieWriter(1209600000L);

    @Test
    void writeRefreshTokenCookie_setsCurrentAndClearsLegacyCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeRefreshTokenCookie(response, "refresh-token", request);

        assertThat(response.getHeaders("Set-Cookie"))
                .anyMatch(header -> header.contains("refreshToken=refresh-token")
                        && header.contains("Path=/api/v1/auth")
                        && header.contains("Max-Age=1209600")
                        && header.contains("Secure")
                        && header.contains("HttpOnly")
                        && header.contains("SameSite=Lax"));
        assertThat(response.getHeaders("Set-Cookie"))
                .anyMatch(header -> header.contains("refreshToken=")
                        && header.contains("Path=/api/v1/auth/refresh")
                        && header.contains("Max-Age=0")
                        && header.contains("Secure"));
    }

    @Test
    void clearRefreshTokenCookie_clearsCurrentAndLegacyCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.clearRefreshTokenCookie(response, request);

        assertThat(response.getHeaders("Set-Cookie"))
                .anyMatch(header -> header.contains("refreshToken=")
                        && header.contains("Path=/api/v1/auth")
                        && header.contains("Max-Age=0")
                        && header.contains("Secure"));
        assertThat(response.getHeaders("Set-Cookie"))
                .anyMatch(header -> header.contains("refreshToken=")
                        && header.contains("Path=/api/v1/auth/refresh")
                        && header.contains("Max-Age=0")
                        && header.contains("Secure"));
    }
}
