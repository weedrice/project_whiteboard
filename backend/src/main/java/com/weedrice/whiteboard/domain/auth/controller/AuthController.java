package com.weedrice.whiteboard.domain.auth.controller;

import com.weedrice.whiteboard.domain.auth.service.AuthService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@RequestBody SignupRequest request) {
        Long userId = authService.signup(request.getLoginId(), request.getPassword(), request.getEmail(),
                request.getDisplayName());
        return ResponseEntity.status(HttpStatus.CREATED).body(new SignupResponse(true, userId));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        String token = authService.login(request.getLoginId(), request.getPassword());
        return ResponseEntity.ok(new LoginResponse(true, token));
    }

    @Data
    public static class SignupRequest {
        private String loginId;
        private String password;
        private String email;
        private String displayName;
    }

    @Data
    public static class SignupResponse {
        private boolean success;
        private Long userId;

        public SignupResponse(boolean success, Long userId) {
            this.success = success;
            this.userId = userId;
        }
    }

    @Data
    public static class LoginRequest {
        private String loginId;
        private String password;
    }

    @Data
    public static class LoginResponse {
        private boolean success;
        private String accessToken;

        public LoginResponse(boolean success, String accessToken) {
            this.success = success;
            this.accessToken = accessToken;
        }
    }
}
