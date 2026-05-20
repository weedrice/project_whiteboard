package com.weedrice.whiteboard.global.log.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.log.dto.ErrorLogResolveRequest;
import com.weedrice.whiteboard.global.log.dto.ErrorLogResponse;
import com.weedrice.whiteboard.global.log.dto.ErrorLogSearchRequest;
import com.weedrice.whiteboard.global.log.dto.ErrorLogStatsResponse;
import com.weedrice.whiteboard.global.log.service.ErrorLogService;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ErrorLogController.class, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
})
@AutoConfigureMockMvc
@Import(ErrorLogControllerTest.TestSecurityConfig.class)
class ErrorLogControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    static class TestSecurityConfig {
        @org.springframework.context.annotation.Bean
        public org.springframework.security.web.SecurityFilterChain filterChain(
                org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ErrorLogService errorLogService;

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

    private CustomUserDetails customUserDetails;
    private CustomUserDetails normalUserDetails;

    @BeforeEach
    void setUp() throws Exception {
        customUserDetails = new CustomUserDetails(1L, "admin@example.com", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
        normalUserDetails = new CustomUserDetails(2L, "user@example.com", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

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

    // ===== GET /api/v1/admin/error-logs =====

    @Test
    @DisplayName("Error log list rejects non-super-admin users")
    void getErrorLogs_forbiddenForNonSuperAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/error-logs")
                .with(user(normalUserDetails))
                .with(csrf()))
                .andExpect(status().isForbidden());

        verify(errorLogService, never()).getErrorLogs(any(ErrorLogSearchRequest.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Error log detail rejects non-super-admin users")
    void getErrorLog_forbiddenForNonSuperAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/error-logs/1")
                .with(user(normalUserDetails))
                .with(csrf()))
                .andExpect(status().isForbidden());

        verify(errorLogService, never()).getErrorLogDetail(anyLong());
    }

    @Test
    @DisplayName("Error log resolve rejects non-super-admin users")
    void resolveErrorLog_forbiddenForNonSuperAdmin() throws Exception {
        mockMvc.perform(put("/api/v1/admin/error-logs/1/resolve")
                .with(user(normalUserDetails))
                .with(csrf()))
                .andExpect(status().isForbidden());

        verify(errorLogService, never()).resolveErrorLog(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("Error log stats rejects non-super-admin users")
    void getErrorLogStats_forbiddenForNonSuperAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/error-logs/stats")
                .with(user(normalUserDetails))
                .with(csrf()))
                .andExpect(status().isForbidden());

        verify(errorLogService, never()).getErrorLogStats();
    }

    @Test
    @DisplayName("Error log list returns results")
    void getErrorLogs_returnsSuccess() throws Exception {
        // given
        ErrorLogResponse.ErrorLogSummary errorLog = ErrorLogResponse.ErrorLogSummary.builder()
                .errorCode("INTERNAL_SERVER_ERROR")
                .errorType("NullPointerException")
                .httpStatus(500)
                .message("Error")
                .requestUri("/api/v1/test")
                .requestMethod("GET")
                .ipAddress("127.0.0.1")
                .build();
        Page<ErrorLogResponse.ErrorLogSummary> page = new PageImpl<>(List.of(errorLog), PageRequest.of(0, 20), 1);
        when(errorLogService.getErrorLogs(any(ErrorLogSearchRequest.class), any(Pageable.class))).thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/admin/error-logs")
                .param("page", "0")
                .param("size", "20")
                .with(user(customUserDetails))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].errorCode").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.data.content[0].httpStatus").value(500))
                .andExpect(jsonPath("$.data.content[0].stackTrace").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(errorLogService).getErrorLogs(any(ErrorLogSearchRequest.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort())
                .containsExactly(Sort.Order.desc("createdAt"), Sort.Order.desc("errorLogId"));
    }

    @Test
    @DisplayName("에러 로그 목록 조회는 공통 페이지 최대 크기를 적용한다")
    void getErrorLogs_clampsPageSize() throws Exception {
        Page<ErrorLogResponse.ErrorLogSummary> emptyPage = new PageImpl<>(Collections.emptyList());
        when(errorLogService.getErrorLogs(any(ErrorLogSearchRequest.class), any(Pageable.class))).thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/admin/error-logs")
                .param("size", "1000")
                .with(user(customUserDetails))
                .with(csrf()))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(errorLogService).getErrorLogs(any(ErrorLogSearchRequest.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("에러 로그 목록 조회는 잘못된 페이지 요청을 검증 오류로 응답한다")
    void getErrorLogs_rejectsInvalidPageRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/error-logs")
                .param("size", "0")
                .with(user(customUserDetails))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    @DisplayName("에러 로그 목록 조회 - 빈 결과")
    void getErrorLogs_emptyResult() throws Exception {
        // given
        Page<ErrorLogResponse.ErrorLogSummary> emptyPage =
                new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
        when(errorLogService.getErrorLogs(any(ErrorLogSearchRequest.class), any(Pageable.class))).thenReturn(emptyPage);

        // when & then
        mockMvc.perform(get("/api/v1/admin/error-logs")
                .with(user(customUserDetails))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("에러 로그 목록 조회 - 필터 파라미터 전달")
    void getErrorLogs_withFilterParams() throws Exception {
        // given
        Page<ErrorLogResponse.ErrorLogSummary> emptyPage = new PageImpl<>(Collections.emptyList());
        when(errorLogService.getErrorLogs(any(ErrorLogSearchRequest.class), any(Pageable.class))).thenReturn(emptyPage);

        // when & then
        mockMvc.perform(get("/api/v1/admin/error-logs")
                .param("page", "0")
                .param("size", "10")
                .param("errorType", "NullPointerException")
                .param("httpStatus", "500")
                .param("isResolved", "N")
                .param("startDate", "2026-02-01")
                .param("endDate", "2026-02-23")
                .with(user(customUserDetails))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(errorLogService).getErrorLogs(any(ErrorLogSearchRequest.class), any(Pageable.class));
    }

    // ===== GET /api/v1/admin/error-logs/{errorLogId} =====

    @Test
    @DisplayName("에러 로그 상세 조회 성공")
    void getErrorLog_returnsSuccess() throws Exception {
        // given
        ErrorLogResponse.ErrorLogDetail errorLog = ErrorLogResponse.ErrorLogDetail.builder()
                .errorLogId(1L)
                .errorCode("INTERNAL_SERVER_ERROR")
                .errorType("NullPointerException")
                .httpStatus(500)
                .message("Cannot invoke method on null")
                .requestUri("/api/v1/posts/1")
                .requestMethod("GET")
                .userId(10L)
                .ipAddress("192.168.1.1")
                .userAgent("Mozilla/5.0")
                .stackTrace("java.lang.NullPointerException\n\tat com.example.Test.method(Test.java:1)")
                .isResolved("N")
                .build();
        when(errorLogService.getErrorLogDetail(1L)).thenReturn(errorLog);

        // when & then
        mockMvc.perform(get("/api/v1/admin/error-logs/1")
                .with(user(customUserDetails))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.errorLogId").value(1))
                .andExpect(jsonPath("$.data.errorCode").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.data.errorType").value("NullPointerException"))
                .andExpect(jsonPath("$.data.httpStatus").value(500))
                .andExpect(jsonPath("$.data.message").value("Cannot invoke method on null"))
                .andExpect(jsonPath("$.data.requestUri").value("/api/v1/posts/1"))
                .andExpect(jsonPath("$.data.requestMethod").value("GET"))
                .andExpect(jsonPath("$.data.userId").value(10))
                .andExpect(jsonPath("$.data.ipAddress").value("192.168.1.1"))
                .andExpect(jsonPath("$.data.stackTrace").exists())
                .andExpect(jsonPath("$.data.isResolved").value("N"));
    }

    // ===== PUT /api/v1/admin/error-logs/{errorLogId}/resolve =====

    @Test
    @DisplayName("에러 로그 확인 처리 성공 - memo 포함")
    void resolveErrorLog_withMemo() throws Exception {
        // given
        ErrorLogResolveRequest request = new ErrorLogResolveRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "memo", "확인 완료");

        doNothing().when(errorLogService).resolveErrorLog(eq(1L), eq(1L), eq("확인 완료"));

        // when & then
        mockMvc.perform(put("/api/v1/admin/error-logs/1/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(user(customUserDetails))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(errorLogService).resolveErrorLog(1L, 1L, "확인 완료");
    }

    @Test
    @DisplayName("에러 로그 확인 처리 성공 - memo 없이 (request body 없음)")
    void resolveErrorLog_withoutBody() throws Exception {
        // given
        doNothing().when(errorLogService).resolveErrorLog(eq(2L), eq(1L), isNull());

        // when & then
        mockMvc.perform(put("/api/v1/admin/error-logs/2/resolve")
                .with(user(customUserDetails))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(errorLogService).resolveErrorLog(2L, 1L, null);
    }

    // ===== GET /api/v1/admin/error-logs/stats =====

    @Test
    @DisplayName("에러 로그 통계 조회 성공")
    void getErrorLogStats_returnsSuccess() throws Exception {
        // given
        ErrorLogStatsResponse stats = ErrorLogStatsResponse.builder()
                .totalCount(100L)
                .unresolvedCount(30L)
                .resolvedCount(70L)
                .serverErrorCount(50L)
                .clientErrorCount(50L)
                .build();
        when(errorLogService.getErrorLogStats()).thenReturn(stats);

        // when & then
        mockMvc.perform(get("/api/v1/admin/error-logs/stats")
                .with(user(customUserDetails))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(100))
                .andExpect(jsonPath("$.data.unresolvedCount").value(30))
                .andExpect(jsonPath("$.data.resolvedCount").value(70));
    }
}
