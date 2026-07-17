package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String AUTHORITIES_KEY = "auth";
    private static final String USER_ID_KEY = "userId";
    private static final String SECURITY_VERSION_KEY = "securityVersion";
    private final SecretKey key;
    private final long accessTokenValidityInMilliseconds;
    private final long refreshTokenValidityInMilliseconds;
    private final CustomUserDetailsService customUserDetailsService;
    private final Clock clock;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.expiration}") long accessTokenValidityInMilliseconds,
                            @Value("${jwt.refresh-token.expiration}") long refreshTokenValidityInMilliseconds,
                            CustomUserDetailsService customUserDetailsService,
                            Clock clock) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenValidityInMilliseconds = accessTokenValidityInMilliseconds;
        this.refreshTokenValidityInMilliseconds = refreshTokenValidityInMilliseconds;
        this.customUserDetailsService = customUserDetailsService;
        this.clock = clock;
    }

    public String createAccessToken(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        Date validity = Date.from(clock.instant().plusMillis(this.accessTokenValidityInMilliseconds));

        return Jwts.builder()
                .subject(authentication.getName())
                .claim(USER_ID_KEY, userDetails.getUserId())
                .claim(SECURITY_VERSION_KEY, userDetails.getSecurityVersion())
                .claim(AUTHORITIES_KEY, authorities)
                .expiration(validity)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String createRefreshToken(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Date validity = Date.from(clock.instant().plusMillis(this.refreshTokenValidityInMilliseconds));

        return Jwts.builder()
                .subject(authentication.getName())
                .claim(USER_ID_KEY, userDetails.getUserId())
                .id(UUID.randomUUID().toString())
                .expiration(validity)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Authentication getAuthentication(String accessToken) {
        Claims claims = parseClaims(accessToken);

        if (claims.get(AUTHORITIES_KEY) == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // DB에서 최신 유저 정보 조회 (상태 체크 포함)
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(claims.getSubject());
        if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()
                || !userDetails.isAccountNonExpired() || !userDetails.isCredentialsNonExpired()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!(userDetails instanceof CustomUserDetails customUserDetails)
                || tokenSecurityVersion(claims) != customUserDetails.getSecurityVersion()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    private long tokenSecurityVersion(Claims claims) {
        Number version = claims.get(SECURITY_VERSION_KEY, Number.class);
        return version == null ? 0L : version.longValue();
    }

    public boolean validateToken(String token) {
        try {
            createParser().parseSignedClaims(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.debug("Invalid JWT signature");
        } catch (ExpiredJwtException e) {
            log.debug("Expired JWT token");
        } catch (UnsupportedJwtException e) {
            log.debug("Unsupported JWT token");
        } catch (IllegalArgumentException e) {
            log.debug("Invalid JWT token");
        }
        return false;
    }

    private Claims parseClaims(String accessToken) {
        try {
            return createParser().parseSignedClaims(accessToken).getPayload();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    private JwtParser createParser() {
        return Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public long getAccessTokenValidityInMilliseconds() {
        return accessTokenValidityInMilliseconds;
    }

    public long getRefreshTokenValidityInMilliseconds() {
        return refreshTokenValidityInMilliseconds;
    }
}
