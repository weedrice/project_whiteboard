package com.weedrice.whiteboard.domain.auth.controller;

import com.weedrice.whiteboard.domain.auth.dto.EmailVerificationRequest;
import com.weedrice.whiteboard.domain.auth.dto.FindIdRequest;
import com.weedrice.whiteboard.domain.auth.dto.FindIdResponse;
import com.weedrice.whiteboard.domain.auth.dto.LoginResult;
import com.weedrice.whiteboard.domain.auth.dto.LoginRequest;
import com.weedrice.whiteboard.domain.auth.dto.LoginResponse;
import com.weedrice.whiteboard.domain.auth.dto.PasswordResetByCodeRequest;
import com.weedrice.whiteboard.domain.auth.dto.PasswordResetConfirmRequest;
import com.weedrice.whiteboard.domain.auth.dto.PasswordResetRequest;
import com.weedrice.whiteboard.domain.auth.dto.ReregisterCheckResponse;
import com.weedrice.whiteboard.domain.auth.dto.RefreshResponse;
import com.weedrice.whiteboard.domain.auth.dto.SignupRequest;
import com.weedrice.whiteboard.domain.auth.dto.SignupResponse;
import com.weedrice.whiteboard.domain.auth.dto.TokenResponse;
import com.weedrice.whiteboard.domain.auth.dto.VerifyCodeRequest;
import com.weedrice.whiteboard.domain.auth.dto.VerifyCodeResponse;
import com.weedrice.whiteboard.domain.auth.service.AuthService;
import com.weedrice.whiteboard.domain.auth.service.LoginClientMetadata;
import com.weedrice.whiteboard.domain.auth.service.LoginClientMetadataResolver;
import com.weedrice.whiteboard.domain.auth.service.VerificationCodeService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.annotation.ApiCommonResponses;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.RefreshTokenCookieWriter;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import static com.weedrice.whiteboard.global.security.AuthenticatedUserResolver.optionalUserId;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "인증", description = "회원가입, 로그인, 토큰 갱신 등 인증 관련 API")
public class AuthController {

    private final AuthService authService;
    private final VerificationCodeService verificationCodeService; // Inject VerificationCodeService
    private final RefreshTokenCookieWriter refreshTokenCookieWriter;
    private final LoginClientMetadataResolver loginClientMetadataResolver;

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
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {
        LoginClientMetadata metadata = loginClientMetadataResolver.resolve(httpServletRequest);
        LoginResult result = authService.login(request, metadata);
        refreshTokenCookieWriter.writeRefreshTokenCookie(httpServletResponse, result.getRefreshToken(), httpServletRequest);

        LoginResponse response = LoginResponse.builder()
                .accessToken(result.getAccessToken())
                .expiresIn(result.getExpiresIn())
                .user(result.getUser())
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {
        String refreshToken = resolveRefreshToken(httpServletRequest);
        if (StringUtils.hasText(refreshToken)) {
            authService.logout(refreshToken);
        }
        refreshTokenCookieWriter.clearRefreshTokenCookie(httpServletResponse, httpServletRequest);
        return okVoid();
    }

    @Hidden
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {
        String refreshToken = resolveRefreshToken(httpServletRequest);
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        TokenResponse tokens = authService.refresh(refreshToken);
        refreshTokenCookieWriter.writeRefreshTokenCookie(httpServletResponse, tokens.getRefreshToken(), httpServletRequest);

        RefreshResponse response = RefreshResponse.builder()
                .accessToken(tokens.getAccessToken())
                .expiresIn(tokens.getExpiresIn())
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/email/send-verification")
    public ResponseEntity<ApiResponse<Void>> sendVerificationCode(
            @Valid @RequestBody EmailVerificationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long currentUserId = optionalUserId(userDetails);
        verificationCodeService.sendVerificationCode(
                request.getEmail(),
                request.getPurpose().toPurpose(),
                currentUserId);
        return okVoid();
    }

    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<VerifyCodeResponse>> verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        VerifyCodeResponse response = verificationCodeService.verifyCode(
                request.getEmail(),
                request.getCode(),
                request.getPurpose().toPurpose());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/find-id")
    public ResponseEntity<ApiResponse<FindIdResponse>> findId(@Valid @RequestBody FindIdRequest request) {
        FindIdResponse response = authService.findLoginId(request.getEmail(), request.getVerificationTicket());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "재가입 이메일 확인", description = "해당 이메일이 탈퇴(DELETED) 계정인지 확인하고, 재가입 가능 시 마스킹된 loginId를 반환합니다.")
    @GetMapping("/reregister/check-email")
    public ResponseEntity<ApiResponse<ReregisterCheckResponse>> checkEmailForReregister(
            @RequestParam @jakarta.validation.constraints.Email String email) {
        ReregisterCheckResponse response = authService.checkEmailForReregister(email);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/password/send-reset-link")
    public ResponseEntity<ApiResponse<Void>> sendPasswordResetLink(@Valid @RequestBody PasswordResetRequest request) {
        authService.sendPasswordResetLink(request.getEmail(), request.getVerificationTicket());
        return okVoid();
    }

    @Operation(summary = "비밀번호 초기화 링크 발송 (이메일 입력)", description = "이메일을 입력받아 해당 이메일로 등록된 ID와 비밀번호 초기화 링크를 발송합니다.")
    @PostMapping("/password/send-reset-link-by-email")
    public ResponseEntity<ApiResponse<Void>> sendPasswordResetLinkByEmail(
            @Valid @RequestBody PasswordResetRequest request) {
        authService.sendPasswordResetLinkByEmail(request.getEmail(), request.getVerificationTicket());
        return okVoid();
    }

    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.resetPasswordWithToken(request.getToken(), request.getNewPassword());
        return okVoid();
    }

    @PostMapping("/password/reset-by-code")
    public ResponseEntity<ApiResponse<Void>> resetPasswordByCode(
            @Valid @RequestBody PasswordResetByCodeRequest request) {
        authService.resetPasswordByCode(
                request.getEmail(),
                request.getVerificationTicket(),
                request.getNewPassword());
        return okVoid();
    }

    private ResponseEntity<ApiResponse<Void>> okVoid() {
        return ResponseEntity.ok(ApiResponse.success());
    }

    private String resolveRefreshToken(HttpServletRequest httpServletRequest) {
        Cookie[] cookies = httpServletRequest.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (RefreshTokenCookieWriter.REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName())
                    && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
