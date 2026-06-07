package com.weedrice.whiteboard.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.auth.dto.LoginRequest;
import com.weedrice.whiteboard.domain.auth.dto.LoginResult;
import com.weedrice.whiteboard.domain.auth.dto.SignupRequest;
import com.weedrice.whiteboard.domain.auth.dto.SignupResponse;
import com.weedrice.whiteboard.domain.auth.dto.TokenResponse;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.auth.service.AuthService;
import com.weedrice.whiteboard.domain.auth.service.LoginClientMetadata;
import com.weedrice.whiteboard.domain.auth.service.LoginClientMetadataResolver;
import com.weedrice.whiteboard.domain.auth.service.VerificationCodeService;
import com.weedrice.whiteboard.global.config.CurrentUserIdWebMvcConfig;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CurrentUserIdArgumentResolver;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.RefreshTokenCookieWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    },
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
    })
@TestPropertySource(properties = "jwt.refresh-token.expiration=1209600000")
@Import({
        RefreshTokenCookieWriter.class,
        CurrentUserIdWebMvcConfig.class,
        CurrentUserIdArgumentResolver.class
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private VerificationCodeService verificationCodeService;

    @MockitoBean
    private LoginClientMetadataResolver loginClientMetadataResolver;

    @MockitoBean
    private com.weedrice.whiteboard.global.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private com.weedrice.whiteboard.global.security.JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockitoBean
    private com.weedrice.whiteboard.domain.admin.interceptor.IpBlockInterceptor ipBlockInterceptor;

    @MockitoBean
    private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean
    private com.weedrice.whiteboard.global.security.RefererCheckInterceptor refererCheckInterceptor;

    @MockitoBean
    private com.weedrice.whiteboard.global.ratelimit.RateLimitInterceptor rateLimitInterceptor;

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.clearContext();
        when(ipBlockInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(refererCheckInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(rateLimitInterceptor.preHandle(any(), any(), any())).thenReturn(true);

        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            HttpServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("회원가입 성공")
    void signup_returnsSuccess() throws Exception {
        SignupRequest request = SignupRequest.builder()
                .email("test@example.com")
                .loginId("testuser")
                .password("Password123!")
                .verificationTicket("ticket-1")
                .displayName("Test User")
                .build();
        SignupResponse response = SignupResponse.builder()
                .userId(1L)
                .loginId("testuser")
                .email("test@example.com")
                .displayName("Test User")
                .build();
        when(authService.signup(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(1L));
    }

    @Test
    @DisplayName("로그인 성공")
    void login_returnsSuccess() throws Exception {
        LoginRequest request = new LoginRequest("testuser", "password123");
        LoginResult result = LoginResult.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .expiresIn(1800L)
                .user(null)
                .build();
        LoginClientMetadata metadata = new LoginClientMetadata("127.0.0.1", "test-agent");
        when(loginClientMetadataResolver.resolve(any())).thenReturn(metadata);
        when(authService.login(any(), same(metadata))).thenReturn(result);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(cookie().exists("refreshToken"));
    }

    @Test
    @DisplayName("토큰 갱신 성공")
    void refresh_returnsSuccess() throws Exception {
        TokenResponse response = TokenResponse.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
                .expiresIn(1800L)
                .build();
        when(authService.refresh("refresh-token")).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(cookie().exists("refreshToken"));
    }

    @Test
    @DisplayName("로그아웃은 refresh token 쿠키를 삭제한다")
    void logout_clearsRefreshTokenCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refreshToken", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(cookie().maxAge("refreshToken", 0));
    }

    @Test
    @DisplayName("이메일 인증 실패 메시지는 정상 한국어로 응답한다")
    void verifyCode_failure_returnsReadableMessage() throws Exception {
        doThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "잘못된 인증 코드입니다."))
                .when(verificationCodeService)
                .verifyCode(eq("test@example.com"), eq("000000"), eq(VerificationPurpose.SIGNUP));

        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test@example.com",
                                  "code": "000000",
                                  "purpose": "SIGNUP"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C008"))
                .andExpect(jsonPath("$.error.message").value("잘못된 인증 코드입니다."));
    }

    @Test
    @DisplayName("이메일 인증 요청은 100자를 초과하는 이메일을 거부한다")
    void sendVerificationCode_anonymousPassesNullUserId() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email/send-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test@example.com",
                                  "purpose": "SIGNUP"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(verificationCodeService)
                .sendVerificationCode(eq("test@example.com"), eq(VerificationPurpose.SIGNUP), isNull());
    }

    @Test
    @DisplayName("email verification request passes current user id for authenticated user")
    void sendVerificationCode_authenticatedPassesUserId() throws Exception {
        CustomUserDetails userDetails = new CustomUserDetails(7L, "user@example.com", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

        mockMvc.perform(post("/api/v1/auth/email/send-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new@example.com",
                                  "purpose": "CHANGE_EMAIL"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(verificationCodeService)
                .sendVerificationCode(eq("new@example.com"), eq(VerificationPurpose.CHANGE_EMAIL), eq(7L));
    }

    @Test
    @DisplayName("email verification request passes null user id for anonymous user")
    void sendVerificationCode_rejectsTooLongEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email/send-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "purpose": "SIGNUP"
                                }
                                """.formatted("a".repeat(92) + "@example.com")))
                .andExpect(status().isBadRequest());

        verify(verificationCodeService, never()).sendVerificationCode(any(), any(), any());
    }
}
