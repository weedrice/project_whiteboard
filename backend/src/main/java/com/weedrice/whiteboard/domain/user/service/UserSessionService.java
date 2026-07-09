package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.auth.service.TokenHashService;
import com.weedrice.whiteboard.domain.user.dto.LoginHistoryResponse;
import com.weedrice.whiteboard.domain.user.dto.UserSessionResponse;
import com.weedrice.whiteboard.domain.user.dto.UserSessionRevokeResult;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.RefreshTokenCookieWriter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSessionService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final UserReadableResolver userReadableResolver;
    private final TokenHashService tokenHashService;
    private final Clock clock;

    public List<UserSessionResponse> getActiveSessions(Long userId, HttpServletRequest request) {
        User user = userReadableResolver.resolveActive(userId);
        String currentTokenHash = currentRefreshTokenHash(request);
        LocalDateTime now = now();
        return refreshTokenRepository.findByUserAndIsRevokedAndExpiresAtGreaterThanEqual(user, false, now).stream()
                .sorted(Comparator.comparing(RefreshToken::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .map(token -> UserSessionResponse.from(token, Objects.equals(token.getTokenHash(), currentTokenHash)))
                .toList();
    }

    @Transactional
    public UserSessionRevokeResult revokeSession(Long userId, Long sessionId, HttpServletRequest request) {
        userReadableResolver.resolveActive(userId);
        String currentTokenHash = currentRefreshTokenHash(request);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (refreshToken.getUser() == null || !Objects.equals(refreshToken.getUser().getUserId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!refreshToken.isValidAt(now())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        refreshToken.revoke();

        // Revoked refresh tokens stop future refreshes. Existing access tokens remain valid until their normal TTL.
        return new UserSessionRevokeResult(Objects.equals(refreshToken.getTokenHash(), currentTokenHash));
    }

    @Transactional
    public void revokeOtherSessions(Long userId, HttpServletRequest request) {
        userReadableResolver.resolveActive(userId);
        String currentTokenHash = currentRefreshTokenHash(request);
        if (!StringUtils.hasText(currentTokenHash)) {
            refreshTokenRepository.revokeActiveTokensByUserId(userId, now());
            return;
        }

        // Revoked refresh tokens stop future refreshes. Existing access tokens remain valid until their normal TTL.
        refreshTokenRepository.revokeActiveTokensByUserIdExceptTokenHash(userId, currentTokenHash, now());
    }

    public Page<LoginHistoryResponse> getLoginHistory(Long userId, Pageable pageable) {
        User user = userReadableResolver.resolveActive(userId);
        Page<LoginHistory> histories = loginHistoryRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        return histories.map(LoginHistoryResponse::from);
    }

    private String currentRefreshTokenHash(HttpServletRequest request) {
        String token = currentRefreshToken(request);
        return StringUtils.hasText(token) ? tokenHashService.hashSha256(token) : null;
    }

    private String currentRefreshToken(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (RefreshTokenCookieWriter.REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName())
                    && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
