package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenLifecycleServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenLifecycleService refreshTokenLifecycleService;
    private User user;

    @BeforeEach
    void setUp() {
        refreshTokenLifecycleService = new RefreshTokenLifecycleService(refreshTokenRepository);
        user = User.builder()
                .loginId("user")
                .email("user@test.com")
                .password("password")
                .displayName("User")
                .build();
    }

    @Test
    void revokeActiveRefreshTokens_revokesOnlyRepositorySelectedActiveTokens() {
        RefreshToken activeToken = RefreshToken.builder()
                .user(user)
                .tokenHash("active")
                .ipAddress("127.0.0.1")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(refreshTokenRepository.findByUserAndIsRevokedAndExpiresAtGreaterThanEqual(eq(user), eq(false), any(LocalDateTime.class)))
                .thenReturn(List.of(activeToken));

        refreshTokenLifecycleService.revokeActiveRefreshTokens(user);

        assertThat(activeToken.getIsRevoked()).isTrue();
        verify(refreshTokenRepository).findByUserAndIsRevokedAndExpiresAtGreaterThanEqual(eq(user), eq(false), any(LocalDateTime.class));
        verify(refreshTokenRepository).saveAll(List.of(activeToken));
    }

    @Test
    void revokeActiveRefreshTokens_usesCurrentTimeBoundaryAndSkipsWhenNoActiveTokens() {
        when(refreshTokenRepository.findByUserAndIsRevokedAndExpiresAtGreaterThanEqual(eq(user), eq(false), any(LocalDateTime.class)))
                .thenReturn(List.of());

        refreshTokenLifecycleService.revokeActiveRefreshTokens(user);

        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(refreshTokenRepository).findByUserAndIsRevokedAndExpiresAtGreaterThanEqual(eq(user), eq(false), nowCaptor.capture());
        assertThat(nowCaptor.getValue()).isBeforeOrEqualTo(LocalDateTime.now());
        verify(refreshTokenRepository, never()).saveAll(any());
    }
}
