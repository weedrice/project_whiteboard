package com.weedrice.whiteboard.domain.auth.controller;

import com.weedrice.whiteboard.domain.auth.service.AuthService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        Long userId = authService.signup(
                request.getLoginId(),
                request.getPassword(),
                request.getEmail(),
                request.getDisplayName()
        );

        SignupResponse response = new SignupResponse(userId, request.getLoginId(), request.getEmail(), request.getDisplayName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        AuthService.TokenResponse tokenResponse = authService.login(
                request.getLoginId(),
                request.getPassword(),
                ipAddress,
                userAgent
        );

        LoginResponse response = new LoginResponse(
                tokenResponse.getAccessToken(),
                tokenResponse.getRefreshToken(),
                tokenResponse.getExpiresIn()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthService.TokenResponse tokenResponse = authService.refreshToken(request.getRefreshToken());

        RefreshResponse response = new RefreshResponse(
                tokenResponse.getAccessToken(),
                tokenResponse.getExpiresIn()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    // Request DTOs
    @Data
    public static class SignupRequest {
        @NotBlank(message = "로그인 ID는 필수입니다")
        @Size(min = 4, max = 30, message = "로그인 ID는 4-30자여야 합니다")
        private String loginId;

        @NotBlank(message = "비밀번호는 필수입니다")
        @Size(min = 8, max = 20, message = "비밀번호는 8-20자여야 합니다")
        private String password;

        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "올바른 이메일 형식이 아닙니다")
        private String email;

        @NotBlank(message = "표시 이름은 필수입니다")
        @Size(min = 2, max = 50, message = "표시 이름은 2-50자여야 합니다")
        private String displayName;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "로그인 ID는 필수입니다")
        private String loginId;

        @NotBlank(message = "비밀번호는 필수입니다")
        private String password;
    }

    @Data
    public static class LogoutRequest {
        @NotBlank(message = "리프레시 토큰은 필수입니다")
        private String refreshToken;
    }

    @Data
    public static class RefreshRequest {
        @NotBlank(message = "리프레시 토큰은 필수입니다")
        private String refreshToken;
    }

    // Response DTOs
    @Data
    @lombok.AllArgsConstructor
    public static class SignupResponse {
        private Long userId;
        private String loginId;
        private String email;
        private String displayName;
    }

    @Data
    @lombok.AllArgsConstructor
    public static class LoginResponse {
        private String accessToken;
        private String refreshToken;
        private Integer expiresIn;
    }

    @Data
    @lombok.AllArgsConstructor
    public static class RefreshResponse {
        private String accessToken;
        private Integer expiresIn;
    }
}
