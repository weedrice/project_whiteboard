package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    @Transactional
    public Long signup(String loginId, String password, String email, String displayName) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.builder()
                .loginId(loginId)
                .password(passwordEncoder.encode(password))
                .email(email)
                .displayName(displayName)
                .build();

        userRepository.save(user);

        // Create default settings
        UserSettings settings = UserSettings.builder()
                .user(user)
                .build();
        userSettingsRepository.save(settings);

        return user.getUserId();
    }

    @Transactional
    public TokenResponse login(String loginId, String password, String ipAddress, String userAgent) {
        User user = userRepository.findByLoginId(loginId)
                .orElse(null);

        try {
            UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(loginId, password);
            Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

            if (user != null) {
                // Check account status
                if ("SUSPENDED".equals(user.getStatus())) {
                    loginHistoryRepository.save(LoginHistory.failure(loginId, ipAddress, userAgent, "ACCOUNT_SUSPENDED"));
                    throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
                }
                if ("DELETED".equals(user.getStatus())) {
                    loginHistoryRepository.save(LoginHistory.failure(loginId, ipAddress, userAgent, "ACCOUNT_DELETED"));
                    throw new BusinessException(ErrorCode.ACCOUNT_DELETED);
                }

                // Update last login
                user.updateLastLogin();

                // Create tokens
                String accessToken = jwtTokenProvider.createAccessToken(authentication);
                String refreshTokenValue = jwtTokenProvider.createRefreshToken(authentication);

                // Save refresh token
                RefreshToken refreshToken = RefreshToken.builder()
                        .user(user)
                        .tokenHash(hashToken(refreshTokenValue))
                        .deviceInfo(userAgent)
                        .ipAddress(ipAddress)
                        .expiresAt(LocalDateTime.now().plusDays(14))
                        .build();
                refreshTokenRepository.save(refreshToken);

                // Save login history
                loginHistoryRepository.save(LoginHistory.success(user, loginId, ipAddress, userAgent));

                return TokenResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshTokenValue)
                        .expiresIn(1800) // 30 minutes
                        .build();
            }

            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            loginHistoryRepository.save(LoginHistory.failure(loginId, ipAddress, userAgent, "WRONG_PASSWORD"));
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    @Transactional
    public void logout(String refreshToken) {
        String tokenHash = hashToken(refreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        token.revoke();
    }

    @Transactional
    public TokenResponse refreshToken(String refreshTokenValue) {
        String tokenHash = hashToken(refreshTokenValue);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (!refreshToken.isValid()) {
            if (refreshToken.isExpired()) {
                throw new BusinessException(ErrorCode.EXPIRED_REFRESH_TOKEN);
            }
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = refreshToken.getUser();

        // Create new authentication
        UsernamePasswordAuthenticationToken authenticationToken =
            new UsernamePasswordAuthenticationToken(user.getLoginId(), null, null);

        String accessToken = jwtTokenProvider.createAccessToken(authenticationToken);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .expiresIn(1800)
                .build();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    @lombok.Builder
    @lombok.Getter
    public static class TokenResponse {
        private String accessToken;
        private String refreshToken;
        private Integer expiresIn;
    }
}
