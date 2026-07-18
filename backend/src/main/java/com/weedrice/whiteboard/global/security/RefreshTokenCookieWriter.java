package com.weedrice.whiteboard.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
public class RefreshTokenCookieWriter {

    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/v1/auth";
    private static final String LEGACY_API_REFRESH_TOKEN_COOKIE_PATH = "/api/v1";
    private static final String LEGACY_REFRESH_TOKEN_COOKIE_PATH = "/api/v1/auth/refresh";

    @Getter
    private final long refreshTokenValidityInMilliseconds;
    private final boolean productionProfile;

    public RefreshTokenCookieWriter(
            @Value("${jwt.refresh-token.expiration}") long refreshTokenValidityInMilliseconds,
            Environment environment) {
        this.refreshTokenValidityInMilliseconds = refreshTokenValidityInMilliseconds;
        this.productionProfile = environment != null && environment.acceptsProfiles(Profiles.of("prod"));
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
        clearLegacyRefreshTokenCookies(response, secure);
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
        clearLegacyRefreshTokenCookies(response, secure);
    }

    private void clearLegacyRefreshTokenCookies(HttpServletResponse response, boolean secure) {
        clearRefreshTokenCookieAtPath(response, secure, LEGACY_API_REFRESH_TOKEN_COOKIE_PATH);
        clearRefreshTokenCookieAtPath(response, secure, LEGACY_REFRESH_TOKEN_COOKIE_PATH);
    }

    private void clearRefreshTokenCookieAtPath(HttpServletResponse response, boolean secure, String path) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(path)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (productionProfile) {
            return true;
        }
        if (request == null) {
            return true;
        }
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (StringUtils.hasText(forwardedProto)) {
            String firstForwardedProto = forwardedProto.split(",", 2)[0].trim();
            return "https".equalsIgnoreCase(firstForwardedProto);
        }
        return false;
    }
}
