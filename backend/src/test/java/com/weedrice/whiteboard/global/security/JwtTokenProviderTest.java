package com.weedrice.whiteboard.global.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    private final String secret = "c2VjcmV0LWtleS1mb3ItdGVzdGluZy1wdXJwb3Nlcy1vbmx5LWRvLW5vdC11c2UtaW4tcHJvZHVjdGlvbg==";
    private final long accessTokenValidity = 3600000;
    private final long refreshTokenValidity = 7200000;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(secret, accessTokenValidity, refreshTokenValidity, customUserDetailsService);
    }

    @Test
    @DisplayName("액세스 토큰 생성 및 유효성 검증")
    void createAndValidateAccessToken() {
        // given
        CustomUserDetails userDetails = new CustomUserDetails(1L, "test@test.com", "pass",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());

        // when
        String token = jwtTokenProvider.createAccessToken(authentication);

        // then
        assertThat(token).isNotNull();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("토큰에서 인증 정보 조회")
    void getAuthentication() {
        // given
        CustomUserDetails userDetails = new CustomUserDetails(1L, "test@test.com", "pass",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
        String token = jwtTokenProvider.createAccessToken(authentication);

        when(customUserDetailsService.loadUserByUsername("test@test.com")).thenReturn(userDetails);

        // when
        Authentication authResult = jwtTokenProvider.getAuthentication(token);

        // then
        assertThat(authResult.getName()).isEqualTo("test@test.com");
        assertThat(authResult.getAuthorities()).hasSize(1);
    }

    @Test
    @DisplayName("잘못된 토큰 검증 실패")
    void validateToken_invalid() {
        assertThat(jwtTokenProvider.validateToken("invalid-token")).isFalse();
    }
}