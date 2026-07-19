package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.auth.service.TokenHashService;
import com.weedrice.whiteboard.domain.notification.service.NotificationAccessInvalidationService;
import com.weedrice.whiteboard.domain.user.dto.LoginHistoryResponse;
import com.weedrice.whiteboard.domain.user.dto.UserSessionResponse;
import com.weedrice.whiteboard.domain.user.dto.UserSessionRevokeResult;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.RefreshTokenCookieWriter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSessionService {

    private static final int DEFAULT_LOGIN_HISTORY_PAGE_SIZE = 10;
    private static final Sort DEFAULT_LOGIN_HISTORY_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("historyId"));
    private static final Set<String> ALLOWED_LOGIN_HISTORY_SORTS = Set.of(
            "createdAt", "historyId", "isSuccess");

    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final UserReadableResolver userReadableResolver;
    private final UserRepository userRepository;
    private final TokenHashService tokenHashService;
    private final NotificationAccessInvalidationService notificationAccessInvalidationService;
    private final Clock clock;

    public List<UserSessionResponse> getActiveSessions(Long userId, HttpServletRequest request) {
        User user = userReadableResolver.resolveActive(userId);
        UUID currentSessionFamilyId = currentSessionFamilyId(request);
        LocalDateTime now = now();
        LinkedHashMap<UUID, RefreshToken> latestByFamily = new LinkedHashMap<>();
        refreshTokenRepository.findByUserAndIsRevokedAndExpiresAtGreaterThanEqual(user, false, now).stream()
                .sorted(Comparator.comparing(RefreshToken::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .forEach(token -> latestByFamily.putIfAbsent(token.getSessionFamilyId(), token));
        return latestByFamily.values().stream()
                .map(token -> UserSessionResponse.from(
                        token,
                        Objects.equals(token.getSessionFamilyId(), currentSessionFamilyId)))
                .toList();
    }

    @Transactional
    public UserSessionRevokeResult revokeSession(Long userId, Long sessionId, HttpServletRequest request) {
        userReadableResolver.resolveActive(userId);
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UUID currentSessionFamilyId = currentSessionFamilyId(request);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (refreshToken.getUser() == null || !Objects.equals(refreshToken.getUser().getUserId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!refreshToken.isValidAt(now())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        refreshTokenRepository.revokeTokenFamily(refreshToken.getSessionFamilyId());
        notificationAccessInvalidationService.disconnectSessionFamilyAfterCommit(
                userId,
                refreshToken.getSessionFamilyId());

        // Revoked refresh tokens stop future refreshes. Existing access tokens remain valid until their normal TTL.
        return new UserSessionRevokeResult(
                Objects.equals(refreshToken.getSessionFamilyId(), currentSessionFamilyId));
    }

    @Transactional
    public void revokeOtherSessions(Long userId, HttpServletRequest request) {
        userReadableResolver.resolveActive(userId);
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UUID currentSessionFamilyId = currentSessionFamilyId(request);
        if (currentSessionFamilyId == null) {
            refreshTokenRepository.revokeActiveTokensByUserId(userId, now());
            notificationAccessInvalidationService.disconnectOtherSessionFamiliesAfterCommit(userId, null);
            return;
        }

        // Revoked refresh tokens stop future refreshes. Existing access tokens remain valid until their normal TTL.
        refreshTokenRepository.revokeActiveTokensByUserIdExceptFamily(userId, currentSessionFamilyId, now());
        notificationAccessInvalidationService.disconnectOtherSessionFamiliesAfterCommit(
                userId,
                currentSessionFamilyId);
    }

    public Page<LoginHistoryResponse> getLoginHistory(Long userId, Pageable pageable) {
        User user = userReadableResolver.resolveActive(userId);
        Pageable safePageable = PageRequestUtils.of(
                pageable,
                DEFAULT_LOGIN_HISTORY_PAGE_SIZE,
                DEFAULT_LOGIN_HISTORY_SORT,
                ALLOWED_LOGIN_HISTORY_SORTS);
        Page<LoginHistory> histories = loginHistoryRepository.findByUser(user, safePageable);
        return histories.map(LoginHistoryResponse::from);
    }

    private String currentRefreshTokenHash(HttpServletRequest request) {
        String token = currentRefreshToken(request);
        return StringUtils.hasText(token) ? tokenHashService.hashSha256(token) : null;
    }

    private UUID currentSessionFamilyId(HttpServletRequest request) {
        String currentTokenHash = currentRefreshTokenHash(request);
        if (!StringUtils.hasText(currentTokenHash)) {
            return null;
        }
        return refreshTokenRepository.findRenewalCandidateByTokenHash(currentTokenHash)
                .map(RefreshTokenRepository.RefreshTokenRenewalCandidate::getSessionFamilyId)
                .orElse(null);
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
