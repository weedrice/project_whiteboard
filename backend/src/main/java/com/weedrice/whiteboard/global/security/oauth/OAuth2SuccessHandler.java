package com.weedrice.whiteboard.global.security.oauth;

import com.weedrice.whiteboard.global.security.JwtTokenProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;

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

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        // String email = oAuth2User.getAttribute("email"); // Assuming email is
        // available

        // Generate tokens
        String accessToken = jwtTokenProvider.createAccessToken(authentication);
        String refreshToken = jwtTokenProvider.createRefreshToken(authentication);

        // Put tokens in URL fragment instead of query string to reduce leakage via server/proxy/referrer logs.
        String fragment = "accessToken=" + UriUtils.encodeQueryParam(accessToken, StandardCharsets.UTF_8)
                + "&refreshToken=" + UriUtils.encodeQueryParam(refreshToken, StandardCharsets.UTF_8);
        String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/auth/oauth/callback")
                .fragment(fragment)
                .build(true)
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
