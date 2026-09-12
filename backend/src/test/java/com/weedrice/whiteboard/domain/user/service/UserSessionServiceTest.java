package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.notification.service.NotificationAccessInvalidationService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.SessionAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserSessionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Mock RefreshTokenRepository tokens;
    @Mock LoginHistoryRepository histories;
    @Mock UserReadableResolver users;
    @Mock UserRepository userRepository;
    @Mock NotificationAccessInvalidationService notificationAccessInvalidationService;
    UserSessionService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new UserSessionService(tokens, histories, users, userRepository,
                notificationAccessInvalidationService,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        user = mock(User.class);
        when(user.getUserId()).thenReturn(1L);
        when(users.resolveActive(1L)).thenReturn(user);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void activeSessionsMarkAuthenticatedSessionWithoutCookieAndSortNewestFirst() {
        RefreshToken oldToken = token(10L, user, "old", NOW.minusDays(1), NOW.plusDays(1), false);
        RefreshToken current = token(11L, user, "current-hash", NOW, NOW.plusDays(1), false);
        when(tokens.findByUserAndIsRevokedAndExpiresAtGreaterThanEqual(user, false, NOW))
                .thenReturn(List.of(oldToken, current));

        var sessions = service.getActiveSessions(1L, session(1L, "current-hash"));

        assertTrue(sessions.getFirst().isCurrent());
        assertFalse(sessions.getLast().isCurrent());
    }

    @Test
    void revokeCurrentSessionReturnsLogoutRequired() {
        RefreshToken current = token(12L, user, "hash", NOW, NOW.plusDays(1), false);
        when(tokens.findByTokenIdForUpdate(12L)).thenReturn(Optional.of(current));
        assertTrue(service.revokeSession(1L, 12L, session(1L, "hash")).currentSessionRevoked());
        verify(tokens).revokeTokenFamily(family("hash"));
        verify(notificationAccessInvalidationService)
                .disconnectSessionFamilyAfterCommit(1L, family("hash"));
    }

    @Test
    void revokeRejectsMissingForeignAndExpiredSessions() {
        when(tokens.findByTokenIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.revokeSession(1L, 99L, session(1L, "current")));

        User another = mock(User.class);
        when(another.getUserId()).thenReturn(2L);
        RefreshToken foreign = token(13L, another, "foreign", NOW, NOW.plusDays(1), false);
        when(tokens.findByTokenIdForUpdate(13L)).thenReturn(Optional.of(foreign));
        assertThrows(BusinessException.class, () -> service.revokeSession(1L, 13L, session(1L, "current")));

        RefreshToken expired = token(14L, user, "expired", NOW, NOW.minusSeconds(1), false);
        when(tokens.findByTokenIdForUpdate(14L)).thenReturn(Optional.of(expired));
        assertThrows(BusinessException.class, () -> service.revokeSession(1L, 14L, session(1L, "current")));
    }

    @Test
    void revokeOtherSessionsPreservesAuthenticatedFamilyWithoutCookie() {
        service.revokeOtherSessions(1L, session(1L, "keep-hash"));

        verify(tokens).revokeActiveTokensByUserIdExceptFamily(1L, family("keep-hash"), NOW);
        verify(tokens, never()).revokeActiveTokensByUserId(any(), any());
        verify(notificationAccessInvalidationService)
                .disconnectOtherSessionFamiliesAfterCommit(1L, family("keep-hash"));
    }

    @Test
    void revokingAnotherSessionDoesNotLogOutCurrentSession() {
        RefreshToken other = token(15L, user, "other", NOW, NOW.plusDays(1), false);
        when(tokens.findByTokenIdForUpdate(15L)).thenReturn(Optional.of(other));

        assertFalse(service.revokeSession(1L, 15L, session(1L, "current")).currentSessionRevoked());
        verify(tokens).revokeTokenFamily(family("other"));
    }

    @ParameterizedTest
    @MethodSource("untrustedSessionAuthentications")
    void rejectsUnknownOrMismatchedSessionWithoutRevokingAnything(Authentication authentication) {
        assertThat(assertThrows(BusinessException.class,
                () -> service.getActiveSessions(1L, authentication)).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(assertThrows(BusinessException.class,
                () -> service.revokeSession(1L, 12L, authentication)).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(assertThrows(BusinessException.class,
                () -> service.revokeOtherSessions(1L, authentication)).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(tokens, notificationAccessInvalidationService);
    }

    private static Stream<Arguments> untrustedSessionAuthentications() {
        SessionAuthenticationToken unauthenticated = session(1L, "current");
        unauthenticated.setAuthenticated(false);
        return Stream.of(
                Arguments.of((Authentication) null),
                Arguments.of(unauthenticated),
                Arguments.of(session(2L, "foreign")),
                Arguments.of(UsernamePasswordAuthenticationToken.authenticated(
                        new CustomUserDetails(1L, "user", "unused", List.of()), null, List.of())));
    }

    @Test
    void mapsLoginHistoryPage() {
        LoginHistory history = mock(LoginHistory.class);
        when(history.getIpAddress()).thenReturn("127.0.0.1");
        when(history.getUserAgent()).thenReturn("Browser");
        PageRequest pageable = PageRequest.of(0, 10);
        when(histories.findByUser(user, PageRequest.of(0, 10, Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.desc("historyId")))))
                .thenReturn(new PageImpl<>(List.of(history), pageable, 1));

        assertTrue(service.getLoginHistory(1L, pageable).hasContent());
    }

    @Test
    void loginHistoryWhitelistsSortAndAddsStableFallback() {
        when(histories.findByUser(eq(user), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));
        Pageable requested = PageRequest.of(2, 500, Sort.by(
                Sort.Order.asc("isSuccess"),
                Sort.Order.desc("ipAddress")));

        service.getLoginHistory(1L, requested);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(histories).findByUser(eq(user), pageableCaptor.capture());
        Pageable actual = pageableCaptor.getValue();
        assertThat(actual.getPageNumber()).isEqualTo(2);
        assertThat(actual.getPageSize()).isEqualTo(100);
        assertThat(actual.getSort()).containsExactly(
                Sort.Order.asc("isSuccess"),
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("historyId"));
    }

    private static RefreshToken token(Long id, User owner, String hash, LocalDateTime createdAt,
            LocalDateTime expiresAt, boolean revoked) {
        RefreshToken token = mock(RefreshToken.class);
        when(token.getTokenId()).thenReturn(id);
        when(token.getUser()).thenReturn(owner);
        when(token.getTokenHash()).thenReturn(hash);
        when(token.getSessionFamilyId()).thenReturn(family(hash));
        when(token.getCreatedAt()).thenReturn(createdAt);
        when(token.getExpiresAt()).thenReturn(expiresAt);
        when(token.getIpAddress()).thenReturn("127.0.0.1");
        when(token.isValidAt(NOW)).thenReturn(!revoked && !expiresAt.isBefore(NOW));
        return token;
    }

    private static UUID family(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static SessionAuthenticationToken session(Long userId, String familyKey) {
        CustomUserDetails principal = new CustomUserDetails(userId, "user-" + userId, "unused", List.of());
        return new SessionAuthenticationToken(principal, principal.getAuthorities(), family(familyKey));
    }
}
