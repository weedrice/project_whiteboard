package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.notification.service.NotificationAccessInvalidationService;
import com.weedrice.whiteboard.domain.user.dto.LoginHistoryResponse;
import com.weedrice.whiteboard.domain.user.dto.UserSessionResponse;
import com.weedrice.whiteboard.domain.user.dto.UserSessionRevokeResult;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.SessionAuthenticationToken;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final NotificationAccessInvalidationService notificationAccessInvalidationService;
    private final Clock clock;

    public List<UserSessionResponse> getActiveSessions(Long userId, Authentication authentication) {
        User user = userReadableResolver.resolveActive(userId);
        UUID currentSessionFamilyId = currentSessionFamilyId(userId, authentication);
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
    public UserSessionRevokeResult revokeSession(Long userId, Long sessionId, Authentication authentication) {
        userReadableResolver.resolveActive(userId);
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UUID currentSessionFamilyId = currentSessionFamilyId(userId, authentication);
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

        // JWT authentication checks the active session family on subsequent requests.
        return new UserSessionRevokeResult(
                Objects.equals(refreshToken.getSessionFamilyId(), currentSessionFamilyId));
    }

    @Transactional
    public void revokeOtherSessions(Long userId, Authentication authentication) {
        userReadableResolver.resolveActive(userId);
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UUID currentSessionFamilyId = currentSessionFamilyId(userId, authentication);
        // JWT authentication checks the active session family on subsequent requests.
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

    private UUID currentSessionFamilyId(Long userId, Authentication authentication) {
        // /users/me/sessions is outside the refresh-cookie path. Use only the session
        // identity already verified by JwtTokenProvider, never a client-supplied cookie/header.
        if (!(authentication instanceof SessionAuthenticationToken sessionAuthentication)
                || !sessionAuthentication.isAuthenticated()
                || !(sessionAuthentication.getPrincipal() instanceof CustomUserDetails principal)
                || userId == null
                || !Objects.equals(userId, principal.getUserId())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return sessionAuthentication.getSessionFamilyId();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
