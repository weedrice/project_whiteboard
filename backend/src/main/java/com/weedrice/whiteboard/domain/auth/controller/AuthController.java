package com.weedrice.whiteboard.domain.auth.controller;

import com.weedrice.whiteboard.domain.auth.dto.EmailVerificationRequest;
import com.weedrice.whiteboard.domain.auth.dto.FindIdRequest;
import com.weedrice.whiteboard.domain.auth.dto.FindIdResponse;
import com.weedrice.whiteboard.domain.auth.dto.LoginRequest;
import com.weedrice.whiteboard.domain.auth.dto.LoginResponse;
import com.weedrice.whiteboard.domain.auth.dto.LogoutRequest;
import com.weedrice.whiteboard.domain.auth.dto.PasswordResetByCodeRequest;
import com.weedrice.whiteboard.domain.auth.dto.PasswordResetConfirmRequest;
import com.weedrice.whiteboard.domain.auth.dto.PasswordResetRequest;
import com.weedrice.whiteboard.domain.auth.dto.RefreshRequest;
import com.weedrice.whiteboard.domain.auth.dto.RefreshResponse;
import com.weedrice.whiteboard.domain.auth.dto.SignupRequest;
import com.weedrice.whiteboard.domain.auth.dto.SignupResponse;
import com.weedrice.whiteboard.domain.auth.dto.VerifyCodeRequest;
import com.weedrice.whiteboard.domain.auth.service.AuthService;
import com.weedrice.whiteboard.domain.auth.service.VerificationCodeService; // Import VerificationCodeService
import com.weedrice.whiteboard.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final VerificationCodeService verificationCodeService; // Inject VerificationCodeService

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest) {
        LoginResponse response = authService.login(request, httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/email/send-verification")
    public ResponseEntity<ApiResponse<Void>> sendVerificationCode(
            @Valid @RequestBody EmailVerificationRequest request) {
        verificationCodeService.sendVerificationCode(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<Void>> verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        verificationCodeService.verifyCode(request.getEmail(), request.getCode());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/find-id")
    public ResponseEntity<ApiResponse<FindIdResponse>> findId(@Valid @RequestBody FindIdRequest request) {
        FindIdResponse response = authService.findLoginId(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/password/send-reset-link")
    public ResponseEntity<ApiResponse<Void>> sendPasswordResetLink(@Valid @RequestBody PasswordResetRequest request) {
        authService.sendPasswordResetLink(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.resetPasswordWithToken(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/password/reset-by-code")
    public ResponseEntity<ApiResponse<Void>> resetPasswordByCode(
            @Valid @RequestBody PasswordResetByCodeRequest request) {
        authService.resetPasswordByCode(request.getEmail(), request.getCode(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
