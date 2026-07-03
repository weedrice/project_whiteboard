package com.weedrice.whiteboard.global.security.oauth;

import com.weedrice.whiteboard.domain.auth.dto.TokenResponse;
import com.weedrice.whiteboard.domain.auth.service.LoginAccountEligibilityService;
import com.weedrice.whiteboard.domain.auth.service.LoginClientMetadata;
import com.weedrice.whiteboard.domain.auth.service.LoginClientMetadataResolver;
import com.weedrice.whiteboard.domain.auth.service.OAuthSignupTicketService;
import com.weedrice.whiteboard.domain.auth.service.SessionTokenService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionPolicyService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.security.RefreshTokenCookieWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

    @Mock
    private SessionTokenService sessionTokenService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SanctionPolicyService sanctionPolicyService;
    @Mock
    private OAuthSignupTicketService oAuthSignupTicketService;

    private OAuth2SuccessHandler handler;
    private User user;

    @BeforeEach
    void setUp() {
        handler = new OAuth2SuccessHandler(
                sessionTokenService,
                userRepository,
                new LoginAccountEligibilityService(sanctionPolicyService),
                new RefreshTokenCookieWriter(1209600000L),
                new LoginClientMetadataResolver(),
                oAuthSignupTicketService);
        ReflectionTestUtils.setField(handler, "frontendUrl", "http://localhost:5173");

        user = User.builder()
                .loginId("oauth-user")
                .password("encoded")
                .email("oauth@example.com")
                .displayName("OAuth User")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
    }

    @Test
    @DisplayName("unregistered oauth user is redirected with opaque signup ticket only")
    void onAuthenticationSuccess_unregisteredUser_redirectsWithSignupTicket() throws Exception {
        UnregisteredOAuth2User principal = new UnregisteredOAuth2User(
                Map.of("id", "provider-user-id"),
                "id",
                "google",
                "provider-user-id",
                "oauth@example.com",
                "OAuth User",
                null);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(oAuthSignupTicketService.issue(
                "oauth@example.com",
                "OAuth User",
                "google",
                "provider-user-id"))
                .thenReturn("ticket-1");

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/signup?oauthRegistrationTicket=ticket-1");
        assertThat(response.getRedirectedUrl()).doesNotContain("oauth@example.com", "providerId");
        verify(sessionTokenService, never()).issueTokens(any(), any(), any());
    }

    @Test
    @DisplayName("banned oauth user is redirected without issuing tokens")
    void onAuthenticationSuccess_bannedUser_redirectsWithoutIssuingTokens() throws Exception {
        CustomOAuth2User principal = new CustomOAuth2User(
                user,
                Map.of("id", "oauth-user"),
                "id",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sanctionPolicyService.isUserBanned(user)).thenReturn(true);

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/auth/oauth/callback");
        verify(sessionTokenService, never()).issueTokens(any(), any(), any());
    }

    @Test
    @DisplayName("deleted-at oauth user is redirected without issuing tokens")
    void onAuthenticationSuccess_deletedAtUser_redirectsWithoutIssuingTokens() throws Exception {
        ReflectionTestUtils.setField(user, "deletedAt", LocalDateTime.now().minusDays(1));
        CustomOAuth2User principal = new CustomOAuth2User(
                user,
                Map.of("id", "oauth-user"),
                "id",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/auth/oauth/callback");
        verify(sessionTokenService, never()).issueTokens(any(), any(), any());
    }

    @Test
    @DisplayName("active oauth user uses shared token issuance flow")
    void onAuthenticationSuccess_activeUser_issuesTokensViaSessionService() throws Exception {
        CustomOAuth2User principal = new CustomOAuth2User(
                user,
                Map.of("id", "oauth-user"),
                "id",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        request.addHeader("User-Agent", "OAuth Browser");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sanctionPolicyService.isUserBanned(user)).thenReturn(false);
        when(sessionTokenService.issueTokens(
                eq(authentication),
                eq(user),
                eq(new LoginClientMetadata("203.0.113.7", "OAuth Browser"))))
                .thenReturn(TokenResponse.builder()
                        .accessToken("issued-access")
                        .refreshToken("issued-refresh")
                        .expiresIn(1800L)
                        .build());

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/auth/oauth/callback");
        assertThat(response.getHeaders("Set-Cookie"))
                .anyMatch(header -> header.contains("refreshToken=issued-refresh")
                        && header.contains("Path=/api/v1/auth")
                        && header.contains("HttpOnly")
                        && header.contains("SameSite=Lax"));
        assertThat(response.getHeaders("Set-Cookie"))
                .anyMatch(header -> header.contains("refreshToken=")
                        && header.contains("Path=/api/v1/auth/refresh")
                        && header.contains("Max-Age=0"));
        verify(sessionTokenService).issueTokens(
                eq(authentication),
                eq(user),
                eq(new LoginClientMetadata("203.0.113.7", "OAuth Browser")));
    }
}
