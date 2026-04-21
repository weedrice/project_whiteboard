package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.FindIdResponse;
import com.weedrice.whiteboard.domain.auth.dto.LoginRequest;
import com.weedrice.whiteboard.domain.auth.dto.LoginResult;
import com.weedrice.whiteboard.domain.auth.dto.ReregisterCheckResponse;
import com.weedrice.whiteboard.domain.auth.dto.SignupRequest;
import com.weedrice.whiteboard.domain.auth.dto.SignupResponse;
import com.weedrice.whiteboard.domain.auth.dto.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SignupService signupService;
    private final SessionTokenService sessionTokenService;
    private final PasswordResetService passwordResetService;

    public SignupResponse signup(SignupRequest request) {
        return signupService.signup(request);
    }

    public LoginResult login(LoginRequest request, HttpServletRequest httpServletRequest) {
        return sessionTokenService.login(request, httpServletRequest);
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
}
