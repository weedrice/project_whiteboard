package com.weedrice.whiteboard.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private Authentication authentication;

    @Test
    @DisplayName("유효한 토큰이 있을 때 인증 객체 설정")
    void doFilterInternal_validToken() throws ServletException, IOException {
        // given
        String token = "valid_token";
        when(request.getHeader(JwtAuthenticationFilter.AUTHORIZATION_HEADER)).thenReturn(JwtAuthenticationFilter.BEARER_PREFIX + token);
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getAuthentication(token)).thenReturn(authentication);

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(authentication);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("토큰이 없으면 인증 객체 설정하지 않음")
    void doFilterInternal_noToken() throws ServletException, IOException {
        // given
        when(request.getHeader(JwtAuthenticationFilter.AUTHORIZATION_HEADER)).thenReturn(null);

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then
        // Context might keep previous value if not cleared, but in unit test it starts empty or we should clear it.
        // Assuming setUp clears context or it's new thread.
        // Better to check if validateToken was NOT called.
        verify(jwtTokenProvider, never()).validateToken(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("유효하지 않은 토큰이면 인증 객체 설정하지 않음")
    void doFilterInternal_invalidToken() throws ServletException, IOException {
        // given
        String token = "invalid_token";
        when(request.getHeader(JwtAuthenticationFilter.AUTHORIZATION_HEADER)).thenReturn(JwtAuthenticationFilter.BEARER_PREFIX + token);
        when(jwtTokenProvider.validateToken(token)).thenReturn(false);

        // when
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // then
        verify(jwtTokenProvider, never()).getAuthentication(anyString());
        verify(filterChain).doFilter(request, response);
    }
}
