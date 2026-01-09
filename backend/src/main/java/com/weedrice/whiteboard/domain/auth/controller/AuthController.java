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
import com.weedrice.whiteboard.domain.auth.service.VerificationCodeService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.annotation.ApiCommonResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "인증", description = "회원가입, 로그인, 토큰 갱신 등 인증 관련 API")
public class AuthController {

    private final AuthService authService;
    private final VerificationCodeService verificationCodeService; // Inject VerificationCodeService

    @Operation(
            summary = "회원가입",
            description = "새로운 사용자를 등록합니다. 이메일 인증이 필요할 수 있습니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "회원가입 성공",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = SignupResponse.class),
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "userId": 1,
                                        "loginId": "user123",
                                        "email": "user@example.com",
                                        "displayName": "사용자"
                                      }
                                    }
                                    """
                    )
            )
    )
    @ApiCommonResponses
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(
            summary = "로그인",
            description = "로그인 ID와 비밀번호로 인증하고 JWT 토큰을 발급받습니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "로그인 성공",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = LoginResponse.class),
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "success": true,
                                      "data": {
                                        "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                                        "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                                        "tokenType": "Bearer",
                                        "expiresIn": 1800
                                      }
                                    }
                                    """
                    )
            )
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "로그인 실패 (잘못된 ID 또는 비밀번호)",
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "success": false,
                                      "error": {
                                        "code": "A003",
                                        "message": "로그인에 실패했습니다."
                                      }
                                    }
                                    """
                    )
            )
    )
    @ApiCommonResponses
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
