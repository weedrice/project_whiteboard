package com.weedrice.whiteboard.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
public class RefreshTokenCookieWriter {

    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/v1/auth";
    private static final String LEGACY_REFRESH_TOKEN_COOKIE_PATH = "/api/v1/auth/refresh";

    @Getter
    private final long refreshTokenValidityInMilliseconds;

    public RefreshTokenCookieWriter(@Value("${jwt.refresh-token.expiration}") long refreshTokenValidityInMilliseconds) {
        this.refreshTokenValidityInMilliseconds = refreshTokenValidityInMilliseconds;
    }

    public void writeRefreshTokenCookie(
            HttpServletResponse response,
            String refreshToken,
            HttpServletRequest request) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        boolean secure = isSecureRequest(request);
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(Duration.ofMillis(refreshTokenValidityInMilliseconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        clearLegacyRefreshTokenCookie(response, secure);
    }

    public void clearRefreshTokenCookie(HttpServletResponse response, HttpServletRequest request) {
        boolean secure = isSecureRequest(request);
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        clearLegacyRefreshTokenCookie(response, secure);
    }

    private void clearLegacyRefreshTokenCookie(HttpServletResponse response, boolean secure) {
        ResponseCookie legacyCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(LEGACY_REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, legacyCookie.toString());
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (request == null) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (StringUtils.hasText(forwardedProto)) {
            return "https".equalsIgnoreCase(forwardedProto);
        }
        return request.isSecure();
    }
}
