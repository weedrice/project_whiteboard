package com.weedrice.whiteboard.global.security.oauth;

import com.weedrice.whiteboard.domain.auth.dto.TokenResponse;
import com.weedrice.whiteboard.domain.auth.service.SessionTokenService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.JwtTokenProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/v1/auth";
    private static final String LEGACY_REFRESH_TOKEN_COOKIE_PATH = "/api/v1/auth/refresh";

    private final JwtTokenProvider jwtTokenProvider;
    private final SessionTokenService sessionTokenService;
    private final UserRepository userRepository;
    private final SanctionService sanctionService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        if (authentication.getPrincipal() instanceof UnregisteredOAuth2User) {
            UnregisteredOAuth2User unregisteredUser = (UnregisteredOAuth2User) authentication.getPrincipal();

            String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/signup")
                    .queryParam("email", unregisteredUser.getEmail())
                    .queryParam("name", unregisteredUser.getName())
                    .queryParam("provider", unregisteredUser.getProvider())
                    .queryParam("providerId", unregisteredUser.getProviderId())
                    .build().toUriString();

            getRedirectStrategy().sendRedirect(request, response, targetUrl);
            return;
        }

        User authenticatedUser = getAuthenticatedActiveUser(authentication);
        if (authenticatedUser == null) {
            String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/auth/oauth/callback")
                    .build(true)
                    .toUriString();
            getRedirectStrategy().sendRedirect(request, response, targetUrl);
            return;
        }

        TokenResponse issuedTokens = sessionTokenService.issueTokens(authentication, authenticatedUser, request);

        // Keep refresh token in HttpOnly cookie; only pass short-lived access token to frontend.
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", issuedTokens.getRefreshToken())
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite("Lax")
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(Duration.ofMillis(jwtTokenProvider.getRefreshTokenValidityInMilliseconds()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        clearLegacyRefreshTokenCookie(response, isSecureRequest(request));

        String fragment = "accessToken=" + UriUtils.encodeQueryParam(
                issuedTokens.getAccessToken(),
                StandardCharsets.UTF_8);
        String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/auth/oauth/callback")
                .fragment(fragment)
                .build(true)
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (request == null) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null && !forwardedProto.isBlank()) {
            return "https".equalsIgnoreCase(forwardedProto);
        }
        return request.isSecure();
    }

    private void clearLegacyRefreshTokenCookie(HttpServletResponse response, boolean secure) {
        ResponseCookie legacyCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(LEGACY_REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, legacyCookie.toString());
    }

    private User getAuthenticatedActiveUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return null;
        }

        return userRepository.findById(userDetails.getUserId())
                .filter(user -> "ACTIVE".equals(user.getStatus()))
                .filter(user -> !sanctionService.isUserBanned(user))
                .orElse(null);
    }
}
