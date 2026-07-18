package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.notification.web.NotificationSseEmitterRegistry;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationStreamSubscriptionService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final NotificationSseEmitterRegistry emitterRegistry;
    private final Clock clock;

    @Transactional
    public SseEmitter subscribe(Long userId, UUID sessionFamilyId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!User.STATUS_ACTIVE.equals(user.getStatus()) || user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        boolean activeFamily = refreshTokenRepository
                .existsBySessionFamilyIdAndUser_UserIdAndIsRevokedFalseAndExpiresAtGreaterThanEqual(
                        sessionFamilyId,
                        userId,
                        LocalDateTime.now(clock));
        if (!activeFamily) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return emitterRegistry.subscribe(userId, sessionFamilyId);
    }
}
