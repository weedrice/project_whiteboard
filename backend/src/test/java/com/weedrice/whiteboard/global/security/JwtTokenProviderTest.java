package com.weedrice.whiteboard.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 7, 7, 12, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            FIXED_NOW.toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC);

    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    private final String secret = "c2VjcmV0LWtleS1mb3ItdGVzdGluZy1wdXJwb3Nlcy1vbmx5LWRvLW5vdC11c2UtaW4tcHJvZHVjdGlvbg==";
    private final long accessTokenValidity = 3600000;
    private final long refreshTokenValidity = 7200000;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                secret,
                accessTokenValidity,
                refreshTokenValidity,
                customUserDetailsService,
                FIXED_CLOCK);
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
    void getAuthentication_rejectsTokenIssuedBeforeSecurityVersionChanged() {
        CustomUserDetails issuedUser = new CustomUserDetails(1L, "test@test.com", "pass", 2L,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                issuedUser, "", issuedUser.getAuthorities());
        String token = jwtTokenProvider.createAccessToken(authentication);
        CustomUserDetails currentUser = new CustomUserDetails(1L, "test@test.com", "pass", 3L,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        when(customUserDetailsService.loadUserByUsername("test@test.com")).thenReturn(currentUser);

        assertThatThrownBy(() -> jwtTokenProvider.getAuthentication(token))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getAuthentication_acceptsLegacyTokenOnlyWhileSecurityVersionIsZero() {
        String legacyToken = Jwts.builder()
                .subject("test@test.com")
                .claim("userId", 1L)
                .claim("auth", "ROLE_USER")
                .expiration(java.util.Date.from(FIXED_CLOCK.instant().plusMillis(accessTokenValidity)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)), Jwts.SIG.HS256)
                .compact();
        CustomUserDetails currentUser = new CustomUserDetails(1L, "test@test.com", "pass", 0L,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        when(customUserDetailsService.loadUserByUsername("test@test.com")).thenReturn(currentUser);

        assertThat(jwtTokenProvider.getAuthentication(legacyToken).getName()).isEqualTo("test@test.com");
    }

    @Test
    @DisplayName("삭제된 계정은 인증 객체를 만들지 않는다")
    void getAuthentication_deletedUser_throws() {
        CustomUserDetails disabledUser = new CustomUserDetails(
                1L,
                "test@test.com",
                "pass",
                false,
                true,
                true,
                true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                disabledUser,
                "",
                disabledUser.getAuthorities());
        String token = jwtTokenProvider.createAccessToken(authentication);

        when(customUserDetailsService.loadUserByUsername("test@test.com")).thenReturn(disabledUser);

        assertThatThrownBy(() -> jwtTokenProvider.getAuthentication(token))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("정지된 계정은 인증 객체를 만들지 않는다")
    void getAuthentication_suspendedUser_throws() {
        CustomUserDetails lockedUser = new CustomUserDetails(
                1L,
                "test@test.com",
                "pass",
                true,
                true,
                true,
                false,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                lockedUser,
                "",
                lockedUser.getAuthorities());
        String token = jwtTokenProvider.createAccessToken(authentication);

        when(customUserDetailsService.loadUserByUsername("test@test.com")).thenReturn(lockedUser);

        assertThatThrownBy(() -> jwtTokenProvider.getAuthentication(token))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("만료된 계정은 인증 객체를 만들지 않는다")
    void getAuthentication_expiredAccount_throws() {
        CustomUserDetails expiredAccountUser = new CustomUserDetails(
                1L,
                "test@test.com",
                "pass",
                true,
                false,
                true,
                true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                expiredAccountUser,
                "",
                expiredAccountUser.getAuthorities());
        String token = jwtTokenProvider.createAccessToken(authentication);

        when(customUserDetailsService.loadUserByUsername("test@test.com")).thenReturn(expiredAccountUser);

        assertThatThrownBy(() -> jwtTokenProvider.getAuthentication(token))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("자격 증명이 만료된 계정은 인증 객체를 만들지 않는다")
    void getAuthentication_expiredCredentials_throws() {
        CustomUserDetails expiredCredentialsUser = new CustomUserDetails(
                1L,
                "test@test.com",
                "pass",
                true,
                true,
                false,
                true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                expiredCredentialsUser,
                "",
                expiredCredentialsUser.getAuthorities());
        String token = jwtTokenProvider.createAccessToken(authentication);

        when(customUserDetailsService.loadUserByUsername("test@test.com")).thenReturn(expiredCredentialsUser);

        assertThatThrownBy(() -> jwtTokenProvider.getAuthentication(token))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("잘못된 토큰 검증 실패")
    void validateToken_invalid() {
        assertThat(jwtTokenProvider.validateToken("invalid-token")).isFalse();
    }

    @Test
    void createRefreshToken_usesUniqueJwtId() {
        CustomUserDetails userDetails = new CustomUserDetails(1L, "test@test.com", "pass",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());

        String firstToken = jwtTokenProvider.createRefreshToken(authentication);
        String secondToken = jwtTokenProvider.createRefreshToken(authentication);

        Claims firstClaims = parseClaims(firstToken);
        Claims secondClaims = parseClaims(secondToken);
        assertThat(firstToken).isNotEqualTo(secondToken);
        assertThat(firstClaims.getId()).isNotBlank();
        assertThat(secondClaims.getId()).isNotBlank();
        assertThat(firstClaims.getId()).isNotEqualTo(secondClaims.getId());
        assertThat(jwtTokenProvider.validateToken(firstToken)).isTrue();
        assertThat(jwtTokenProvider.validateToken(secondToken)).isTrue();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)))
                .clock(() -> java.util.Date.from(FIXED_CLOCK.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
