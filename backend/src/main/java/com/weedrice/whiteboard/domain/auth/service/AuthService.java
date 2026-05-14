package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.FindIdResponse;
import com.weedrice.whiteboard.domain.auth.dto.LoginRequest;
import com.weedrice.whiteboard.domain.auth.dto.LoginResult;
import com.weedrice.whiteboard.domain.auth.dto.ReregisterCheckResponse;
import com.weedrice.whiteboard.domain.auth.dto.SignupRequest;
import com.weedrice.whiteboard.domain.auth.dto.SignupResponse;
import com.weedrice.whiteboard.domain.auth.dto.TokenResponse;
import com.weedrice.whiteboard.domain.auth.service.LoginAccountEligibilityService.LoginAccountEligibility;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String FAILURE_REASON_AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED";

    private final SignupService signupService;
    private final SessionTokenService sessionTokenService;
    private final PasswordResetService passwordResetService;
    private final UserRepository userRepository;
    private final LoginAccountEligibilityService loginAccountEligibilityService;
    private final LoginAuthenticator loginAuthenticator;
    private final LoginAuditRecorder loginAuditRecorder;
    private final LoginUserInfoAssembler loginUserInfoAssembler;

    public SignupResponse signup(SignupRequest request) {
        return signupService.signup(request);
    }

    @Transactional
    public LoginResult login(LoginRequest request, LoginClientMetadata metadata) {
        LoginClientMetadata resolvedMetadata = metadata != null ? metadata : LoginClientMetadata.empty();
        Authentication authentication;
        try {
            authentication = loginAuthenticator.authenticate(request);
        } catch (AuthenticationException exception) {
            loginAuditRecorder.recordFailure(request, resolvedMetadata, resolveAuthenticationFailureReason(exception));
            throw exception;
        }

        User user = loadAuthenticatedUser(request, resolvedMetadata, authentication);
        LoginAccountEligibility eligibility = loginAccountEligibilityService.evaluate(user);
        if (!eligibility.isLoginAllowed()) {
            loginAuditRecorder.recordFailure(request, resolvedMetadata, eligibility.failureReason());
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        TokenResponse issuedTokens = sessionTokenService.issueTokens(
                authentication,
                user,
                resolvedMetadata.ipAddress(),
                resolvedMetadata.userAgent());

        loginAuditRecorder.recordSuccess(request, user, resolvedMetadata);
        user.updateLastLogin();

        return LoginResult.builder()
                .accessToken(issuedTokens.getAccessToken())
                .refreshToken(issuedTokens.getRefreshToken())
                .expiresIn(issuedTokens.getExpiresIn())
                .user(loginUserInfoAssembler.assemble(user))
                .build();
    }

    public void logout(String token) {
        sessionTokenService.logout(token);
    }

    public TokenResponse refresh(String oldRefreshToken) {
        return sessionTokenService.refresh(oldRefreshToken);
    }

    public ReregisterCheckResponse checkEmailForReregister(String email) {
        return signupService.checkEmailForReregister(email);
    }

    public FindIdResponse findLoginId(String email, String verificationTicket) {
        return signupService.findLoginId(email, verificationTicket);
    }

    public void sendPasswordResetLink(String email, String verificationTicket) {
        passwordResetService.sendPasswordResetLink(email, verificationTicket);
    }

    public void sendPasswordResetLinkByEmail(String email, String verificationTicket) {
        passwordResetService.sendPasswordResetLinkByEmail(email, verificationTicket);
    }

    public void resetPasswordWithToken(String rawToken, String newPassword) {
        passwordResetService.resetPasswordWithToken(rawToken, newPassword);
    }

    public void resetPasswordByCode(String email, String verificationTicket, String newPassword) {
        passwordResetService.resetPasswordByCode(email, verificationTicket, newPassword);
    }

    private User loadAuthenticatedUser(
            LoginRequest request,
            LoginClientMetadata metadata,
            Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getUserId();
        if (userId == null) {
            loginAuditRecorder.recordFailure(request, metadata,
                    LoginAccountEligibilityService.FAILURE_REASON_USER_NOT_FOUND);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            loginAuditRecorder.recordFailure(request, metadata,
                    LoginAccountEligibilityService.FAILURE_REASON_USER_NOT_FOUND);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private String resolveAuthenticationFailureReason(AuthenticationException exception) {
        if (exception instanceof DisabledException) {
            return LoginAccountEligibilityService.FAILURE_REASON_USER_NOT_ACTIVE;
        }
        if (exception instanceof LockedException) {
            return LoginAccountEligibilityService.FAILURE_REASON_USER_BANNED;
        }
        return FAILURE_REASON_AUTHENTICATION_FAILED;
    }
}
