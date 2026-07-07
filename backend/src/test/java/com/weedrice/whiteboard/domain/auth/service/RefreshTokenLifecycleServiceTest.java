package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenLifecycleServiceTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 7, 7, 12, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            FIXED_NOW.toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC);

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    private RefreshTokenLifecycleService refreshTokenLifecycleService;
    private User user;

    @BeforeEach
    void setUp() {
        refreshTokenLifecycleService = new RefreshTokenLifecycleService(
                refreshTokenRepository,
                userRepository,
                FIXED_CLOCK);
        user = User.builder()
                .loginId("user")
                .email("user@test.com")
                .password("password")
                .displayName("User")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
    }

    @Test
    void revokeActiveRefreshTokens_locksUserBeforeBulkRevokingActiveTokens() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        refreshTokenLifecycleService.revokeActiveRefreshTokens(user);

        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        InOrder inOrder = inOrder(userRepository, refreshTokenRepository);
        inOrder.verify(userRepository).findByIdForUpdate(1L);
        inOrder.verify(refreshTokenRepository).revokeActiveTokensByUserId(eq(1L), nowCaptor.capture());
        assertThat(nowCaptor.getValue()).isEqualTo(FIXED_NOW);
    }

    @Test
    void revokeActiveRefreshTokens_usesCurrentTimeBoundary() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        refreshTokenLifecycleService.revokeActiveRefreshTokens(user);

        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(refreshTokenRepository).revokeActiveTokensByUserId(eq(1L), nowCaptor.capture());
        assertThat(nowCaptor.getValue()).isEqualTo(FIXED_NOW);
    }

    @Test
    void revokeActiveRefreshTokensForLockedUser_bulkRevokesWithoutLockingUserAgain() {
        refreshTokenLifecycleService.revokeActiveRefreshTokensForLockedUser(user);

        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userRepository, never()).findByIdForUpdate(1L);
        verify(refreshTokenRepository).revokeActiveTokensByUserId(eq(1L), nowCaptor.capture());
        assertThat(nowCaptor.getValue()).isEqualTo(FIXED_NOW);
    }
}
