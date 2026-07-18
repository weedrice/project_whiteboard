package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.notification.web.NotificationSseEmitterRegistry;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationStreamSubscriptionServiceTest {

    private static final UUID FAMILY_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 18, 0, 0);

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock NotificationSseEmitterRegistry emitterRegistry;
    @Mock User user;

    private NotificationStreamSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new NotificationStreamSubscriptionService(
                userRepository,
                refreshTokenRepository,
                emitterRegistry,
                Clock.fixed(Instant.parse("2026-07-18T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void subscribeLocksAndRevalidatesUserAndFamilyBeforeRegisteringEmitter() {
        SseEmitter emitter = new SseEmitter();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(user.getStatus()).thenReturn(User.STATUS_ACTIVE);
        when(refreshTokenRepository
                .existsBySessionFamilyIdAndUser_UserIdAndIsRevokedFalseAndExpiresAtGreaterThanEqual(
                        FAMILY_ID, 1L, NOW))
                .thenReturn(true);
        when(emitterRegistry.subscribe(1L, FAMILY_ID)).thenReturn(emitter);

        assertThat(service.subscribe(1L, FAMILY_ID)).isSameAs(emitter);

        InOrder order = inOrder(userRepository, refreshTokenRepository, emitterRegistry);
        order.verify(userRepository).findByIdForUpdate(1L);
        order.verify(refreshTokenRepository)
                .existsBySessionFamilyIdAndUser_UserIdAndIsRevokedFalseAndExpiresAtGreaterThanEqual(
                        FAMILY_ID, 1L, NOW);
        order.verify(emitterRegistry).subscribe(1L, FAMILY_ID);
    }

    @Test
    void subscribeRejectsInactiveUserBeforeCheckingFamily() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(user.getStatus()).thenReturn("SUSPENDED");

        assertThatThrownBy(() -> service.subscribe(1L, FAMILY_ID))
                .isInstanceOf(BusinessException.class);

        verify(refreshTokenRepository, never())
                .existsBySessionFamilyIdAndUser_UserIdAndIsRevokedFalseAndExpiresAtGreaterThanEqual(
                        FAMILY_ID, 1L, NOW);
        verify(emitterRegistry, never()).subscribe(1L, FAMILY_ID);
    }

    @Test
    void subscribeRejectsRevokedFamilyWithoutRegisteringEmitter() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(user.getStatus()).thenReturn(User.STATUS_ACTIVE);
        when(refreshTokenRepository
                .existsBySessionFamilyIdAndUser_UserIdAndIsRevokedFalseAndExpiresAtGreaterThanEqual(
                        FAMILY_ID, 1L, NOW))
                .thenReturn(false);

        assertThatThrownBy(() -> service.subscribe(1L, FAMILY_ID))
                .isInstanceOf(BusinessException.class);

        verify(emitterRegistry, never()).subscribe(1L, FAMILY_ID);
    }
}
