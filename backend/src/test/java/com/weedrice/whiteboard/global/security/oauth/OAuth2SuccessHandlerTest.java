package com.weedrice.whiteboard.global.security.oauth;

import com.weedrice.whiteboard.domain.auth.dto.TokenResponse;
import com.weedrice.whiteboard.domain.auth.service.SessionTokenService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.security.JwtTokenProvider;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private SessionTokenService sessionTokenService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SanctionService sanctionService;

    private OAuth2SuccessHandler handler;
    private User user;

    @BeforeEach
    void setUp() {
        handler = new OAuth2SuccessHandler(jwtTokenProvider, sessionTokenService, userRepository, sanctionService);
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
        when(sanctionService.isUserBanned(user)).thenReturn(true);

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/auth/oauth/callback");
        verify(sessionTokenService, never()).issueTokens(authentication, user, request);
        verify(jwtTokenProvider, never()).createAccessToken(authentication);
        verify(jwtTokenProvider, never()).createRefreshToken(authentication);
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
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sanctionService.isUserBanned(user)).thenReturn(false);
        when(sessionTokenService.issueTokens(authentication, user, request)).thenReturn(TokenResponse.builder()
                .accessToken("issued-access")
                .refreshToken("issued-refresh")
                .expiresIn(1800L)
                .build());
        when(jwtTokenProvider.getRefreshTokenValidityInMilliseconds()).thenReturn(1209600000L);

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/auth/oauth/callback#accessToken=issued-access");
        verify(sessionTokenService).issueTokens(authentication, user, request);
        verify(jwtTokenProvider, never()).createAccessToken(authentication);
        verify(jwtTokenProvider, never()).createRefreshToken(authentication);
    }
}
