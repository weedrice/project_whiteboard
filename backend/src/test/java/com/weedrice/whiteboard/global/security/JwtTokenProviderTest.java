package com.weedrice.whiteboard.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.JwsHeader; // Added import
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    private static final String AUTHORITIES_KEY = "auth";
    private static final String USER_ID_KEY = "userId";

    private JwtTokenProvider jwtTokenProvider;
    private String secretKey = "c2VjcmV0LWtleS1mb3ItdGVzdGluZy1wdXJwb3Nlcy1vbmx5LWRvLW5vdC11c2UtaW4tcHJvZHVjdGlvbg=="; // Base64 encoded "secret-key-for-testing-purposes-only-do-not-use-in-production"
    private long accessTokenValidity = 3600000;
    private long refreshTokenValidity = 7200000;
    private Key signingKey; // Declare signingKey

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(secretKey, accessTokenValidity, refreshTokenValidity);
        signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey)); // Initialize signingKey
    }

    @Test
    @DisplayName("액세스 토큰 생성 성공")
    void createAccessToken_success() {
        // given
        Authentication authentication = mock(Authentication.class);
        CustomUserDetails userDetails = new CustomUserDetails(1L, "testuser", "password", Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authentication.getAuthorities()).thenReturn((java.util.Collection) Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        when(authentication.getName()).thenReturn("testuser");

        // when
        String token = jwtTokenProvider.createAccessToken(authentication);

        // then
        assertThat(token).isNotNull();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("리프레시 토큰 생성 성공")
    void createRefreshToken_success() {
        // given
        Authentication authentication = mock(Authentication.class);
        CustomUserDetails userDetails = new CustomUserDetails(1L, "testuser", "password", Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authentication.getName()).thenReturn("testuser");

        // when
        String token = jwtTokenProvider.createRefreshToken(authentication);

        // then
        assertThat(token).isNotNull();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("토큰에서 인증 정보 추출 성공")
    void getAuthentication_success() {
        // given
        Authentication authentication = mock(Authentication.class);
        CustomUserDetails userDetails = new CustomUserDetails(1L, "testuser", "password", Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authentication.getAuthorities()).thenReturn((java.util.Collection) Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        when(authentication.getName()).thenReturn("testuser");
        String token = jwtTokenProvider.createAccessToken(authentication);

        // when
        Authentication auth = jwtTokenProvider.getAuthentication(token);

        // then
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("testuser");
        CustomUserDetails principal = (CustomUserDetails) auth.getPrincipal();
        assertThat(principal.getUserId()).isEqualTo(1L);
        assertThat(auth.getAuthorities()).extracting("authority").contains("ROLE_USER");
    }

    @Test
    @DisplayName("유효하지 않은 토큰 검증 실패")
    void validateToken_invalid() {
        String invalidToken = "invalidToken";
        assertThat(jwtTokenProvider.validateToken(invalidToken)).isFalse();
    }

    @Test
    @DisplayName("토큰에서 인증 정보 추출 실패 - 권한 정보 없음")
    void getAuthentication_missingAuthoritiesClaim() {
        // given
        // Create a minimal CustomUserDetails for building the token subject/claim
        CustomUserDetails userDetailsForToken = new CustomUserDetails(1L, "testuser", "password", Collections.emptyList());
        
        // Create a token without authorities claim (note: it's not authentication.getName() now, but "testuser")
        String tokenWithoutAuthorities = Jwts.builder()
                .setSubject("testuser") // Direct username as subject
                .claim(USER_ID_KEY, userDetailsForToken.getUserId())
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenValidity))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.getAuthentication(tokenWithoutAuthorities))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("권한 정보가 없는 토큰입니다.");
    }

    @Test
    @DisplayName("잘못된 형식의 토큰 검증 실패 (MalformedJwtException)")
    void validateToken_malformed() {
        String malformedToken = "malformed.jwt.token";
        assertThat(jwtTokenProvider.validateToken(malformedToken)).isFalse();
    }

    @Test
    @DisplayName("지원되지 않는 토큰 검증 실패 (UnsupportedJwtException)")
    void validateToken_unsupported() {
        // given
        // Create a token with a different algorithm that is not HS256, but can be parsed
        // Jwts.builder().signWith(someOtherKey, SignatureAlgorithm.PS256)... is not easy here
        // The most common way to get UnsupportedJwtException is to provide a token that is valid but
        // signed with an algorithm not supported by the parser's configuration, or the provided key.
        // For testing, we can provide a token that will cause the parsing to fail with UnsupportedJwtException
        // when setSigningKey(HS256) is used.
        // A simple invalid token for testing this would be one signed with a different algorithm,
        // or one that fails signature validation for another reason but leads to this specific exception.
        
        // This token is signed with signingKey (HS256) but we modify the header to something unsupported
        // Or, more simply, use a token generated with a different alg.
        String unsupportedAlgToken = Jwts.builder()
                .setHeaderParam(JwsHeader.KEY_ID, "someId")
                .setSubject("user")
                .signWith(Keys.secretKeyFor(SignatureAlgorithm.HS512)) // Different HS algo
                .compact();

        // when & then
        // The validateToken method uses a key configured for HS256.
        // If it encounters a token signed with HS512 (which it does not expect for the given key),
        // it should throw UnsupportedJwtException (or Malformed/Signature depending on lib behavior)
        // This is tricky as Jwts parser can adapt. But `validateToken` should ultimately return false.
        
        // Let's create a token with a valid structure but from an algorithm that is not HS256 (key type)
        // If the Key used for setSigningKey is of type SecretKey (for HS algs), and the token implies RS alg.
        // Or vice-versa.
        String tokenWithRS256Alg = Jwts.builder().setSubject("user").signWith(Keys.keyPairFor(SignatureAlgorithm.RS256).getPrivate()).compact();

        assertThat(jwtTokenProvider.validateToken(tokenWithRS256Alg)).isFalse();
    }

    @Test
    @DisplayName("비어있거나 null인 토큰 검증 실패 (IllegalArgumentException)")
    void validateToken_illegalArgument() {
        String emptyToken = "";
        assertThat(jwtTokenProvider.validateToken(emptyToken)).isFalse();
        String nullToken = null;
        assertThat(jwtTokenProvider.validateToken(nullToken)).isFalse();
    }

    @Test
    @DisplayName("만료된 토큰 검증 실패")
    void validateToken_expired() {
        // given
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(secretKey, 1, 1);
        Authentication authentication = mock(Authentication.class);
        CustomUserDetails userDetails = new CustomUserDetails(1L, "testuser", "password", Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authentication.getAuthorities()).thenReturn((java.util.Collection) Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        when(authentication.getName()).thenReturn("testuser");
        String token = shortLivedProvider.createAccessToken(authentication);
        
        // This token is generated by shortLivedProvider, so its key is built from secretKey in its constructor.
        // It's correct.


        // wait for expiration
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // when & then
        assertThat(shortLivedProvider.validateToken(token)).isFalse();
    }
}
