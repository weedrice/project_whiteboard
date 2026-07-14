package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.auth.service.TokenHashService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.security.RefreshTokenCookieWriter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserSessionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Mock RefreshTokenRepository tokens;
    @Mock LoginHistoryRepository histories;
    @Mock UserReadableResolver users;
    @Mock TokenHashService hashes;
    UserSessionService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new UserSessionService(tokens, histories, users, hashes,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        user = mock(User.class);
        when(user.getUserId()).thenReturn(1L);
        when(users.resolveActive(1L)).thenReturn(user);
    }

    @Test
    void activeSessionsMarkCurrentCookieAndSortNewestFirst() {
        RefreshToken oldToken = token(10L, user, "old", NOW.minusDays(1), NOW.plusDays(1), false);
        RefreshToken current = token(11L, user, "current-hash", NOW, NOW.plusDays(1), false);
        HttpServletRequest request = requestWithCookie("raw-current");
        when(hashes.hashSha256("raw-current")).thenReturn("current-hash");
        when(tokens.findByUserAndIsRevokedAndExpiresAtGreaterThanEqual(user, false, NOW))
                .thenReturn(List.of(oldToken, current));

        var sessions = service.getActiveSessions(1L, request);

        assertTrue(sessions.getFirst().isCurrent());
        assertFalse(sessions.getLast().isCurrent());
    }

    @Test
    void revokeCurrentSessionReturnsLogoutRequired() {
        RefreshToken current = token(12L, user, "hash", NOW, NOW.plusDays(1), false);
        when(tokens.findByTokenIdForUpdate(12L)).thenReturn(Optional.of(current));
        when(hashes.hashSha256("raw")).thenReturn("hash");

        assertTrue(service.revokeSession(1L, 12L, requestWithCookie("raw")).currentSessionRevoked());
        verify(current).revoke();
    }

    @Test
    void revokeRejectsMissingForeignAndExpiredSessions() {
        when(tokens.findByTokenIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.revokeSession(1L, 99L, null));

        User another = mock(User.class);
        when(another.getUserId()).thenReturn(2L);
        RefreshToken foreign = token(13L, another, "foreign", NOW, NOW.plusDays(1), false);
        when(tokens.findByTokenIdForUpdate(13L)).thenReturn(Optional.of(foreign));
        assertThrows(BusinessException.class, () -> service.revokeSession(1L, 13L, null));

        RefreshToken expired = token(14L, user, "expired", NOW, NOW.minusSeconds(1), false);
        when(tokens.findByTokenIdForUpdate(14L)).thenReturn(Optional.of(expired));
        assertThrows(BusinessException.class, () -> service.revokeSession(1L, 14L, null));
    }

    @Test
    void revokeOtherSessionsChoosesCookieAwareQuery() {
        service.revokeOtherSessions(1L, null);
        verify(tokens).revokeActiveTokensByUserId(1L, NOW);

        when(hashes.hashSha256("keep")).thenReturn("keep-hash");
        service.revokeOtherSessions(1L, requestWithCookie("keep"));
        verify(tokens).revokeActiveTokensByUserIdExceptTokenHash(1L, "keep-hash", NOW);
    }

    @Test
    void mapsLoginHistoryPage() {
        LoginHistory history = mock(LoginHistory.class);
        when(history.getIpAddress()).thenReturn("127.0.0.1");
        when(history.getUserAgent()).thenReturn("Browser");
        PageRequest pageable = PageRequest.of(0, 10);
        when(histories.findByUserOrderByCreatedAtDesc(user, pageable))
                .thenReturn(new PageImpl<>(List.of(history), pageable, 1));

        assertTrue(service.getLoginHistory(1L, pageable).hasContent());
    }

    private static RefreshToken token(Long id, User owner, String hash, LocalDateTime createdAt,
            LocalDateTime expiresAt, boolean revoked) {
        RefreshToken token = mock(RefreshToken.class);
        when(token.getTokenId()).thenReturn(id);
        when(token.getUser()).thenReturn(owner);
        when(token.getTokenHash()).thenReturn(hash);
        when(token.getCreatedAt()).thenReturn(createdAt);
        when(token.getExpiresAt()).thenReturn(expiresAt);
        when(token.getIpAddress()).thenReturn("127.0.0.1");
        when(token.isValidAt(NOW)).thenReturn(!revoked && !expiresAt.isBefore(NOW));
        return token;
    }

    private static HttpServletRequest requestWithCookie(String value) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("ignored", "value"),
                new Cookie(RefreshTokenCookieWriter.REFRESH_TOKEN_COOKIE_NAME, value),
        });
        return request;
    }
}
