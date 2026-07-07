package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenLifecycleService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public void revokeActiveRefreshTokens(User user) {
        Long userId = validateRefreshTokenRevocationTarget(user);
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        revokeActiveTokens(userId);
    }

    @Transactional
    public void revokeActiveRefreshTokensForLockedUser(User user) {
        Long userId = validateRefreshTokenRevocationTarget(user);
        revokeActiveTokens(userId);
    }

    private Long validateRefreshTokenRevocationTarget(User user) {
        if (user == null || user.getUserId() == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user.getUserId();
    }

    private void revokeActiveTokens(Long userId) {
        refreshTokenRepository.revokeActiveTokensByUserId(userId, LocalDateTime.now(clock));
    }
}
