package com.weedrice.whiteboard.global.security.oauth;

import com.weedrice.whiteboard.domain.auth.dto.TokenResponse;
import com.weedrice.whiteboard.domain.auth.service.LoginAccountEligibilityService;
import com.weedrice.whiteboard.domain.auth.service.LoginClientMetadata;
import com.weedrice.whiteboard.domain.auth.service.LoginClientMetadataResolver;
import com.weedrice.whiteboard.domain.auth.service.OAuthSignupTicketService;
import com.weedrice.whiteboard.domain.auth.service.SessionTokenService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.RefreshTokenCookieWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final SessionTokenService sessionTokenService;
    private final UserRepository userRepository;
    private final LoginAccountEligibilityService loginAccountEligibilityService;
    private final RefreshTokenCookieWriter refreshTokenCookieWriter;
    private final LoginClientMetadataResolver loginClientMetadataResolver;
    private final OAuthSignupTicketService oAuthSignupTicketService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        if (authentication.getPrincipal() instanceof UnregisteredOAuth2User) {
            UnregisteredOAuth2User unregisteredUser = (UnregisteredOAuth2User) authentication.getPrincipal();
            String ticket = oAuthSignupTicketService.issue(
                    unregisteredUser.getEmail(),
                    unregisteredUser.getDisplayName(),
                    unregisteredUser.getProvider(),
                    unregisteredUser.getProviderId());

            String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/signup")
                    .queryParam("oauthRegistrationTicket", ticket)
                    .build().toUriString();

            getRedirectStrategy().sendRedirect(request, response, targetUrl);
            return;
        }

        User authenticatedUser = getEligibleAuthenticatedUser(authentication);
        if (authenticatedUser == null) {
            String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/auth/oauth/callback")
                    .build(true)
                    .toUriString();
            getRedirectStrategy().sendRedirect(request, response, targetUrl);
            return;
        }

        LoginClientMetadata metadata = loginClientMetadataResolver.resolve(request);
        TokenResponse issuedTokens = sessionTokenService.issueTokens(
                authentication,
                authenticatedUser,
                metadata);

        // Keep tokens out of URLs; the frontend exchanges the refresh cookie for an access token.
        refreshTokenCookieWriter.writeRefreshTokenCookie(response, issuedTokens.getRefreshToken(), request);

        String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/auth/oauth/callback")
                .build(true)
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private User getEligibleAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return null;
        }

        return userRepository.findById(userDetails.getUserId())
                .filter(user -> loginAccountEligibilityService.evaluate(user).isLoginAllowed())
                .orElse(null);
    }
}
