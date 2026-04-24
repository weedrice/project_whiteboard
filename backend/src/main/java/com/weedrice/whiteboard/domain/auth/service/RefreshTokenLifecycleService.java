package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenLifecycleService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void revokeActiveRefreshTokens(User user) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserAndIsRevokedAndExpiresAtGreaterThanEqual(
                user,
                false,
                LocalDateTime.now());
        List<RefreshToken> tokensToRevoke = activeTokens != null ? activeTokens : Collections.emptyList();
        for (RefreshToken token : tokensToRevoke) {
            token.revoke();
        }
        if (!tokensToRevoke.isEmpty()) {
            refreshTokenRepository.saveAll(tokensToRevoke);
        }
    }
}
