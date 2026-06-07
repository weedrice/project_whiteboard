package com.weedrice.whiteboard.domain.report.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.report.dto.ReportProcessRequest;
import com.weedrice.whiteboard.domain.report.dto.ReportResponse;
import com.weedrice.whiteboard.domain.report.entity.ReportStatus;
import com.weedrice.whiteboard.domain.report.service.ReportService;
import com.weedrice.whiteboard.global.config.CurrentUserIdWebMvcConfig;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CurrentUserIdArgumentResolver;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminReportController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                        classes = com.weedrice.whiteboard.global.config.WebConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                        classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
        })
@AutoConfigureMockMvc
@Import({
        AdminReportControllerTest.TestSecurityConfig.class,
        CurrentUserIdWebMvcConfig.class,
        CurrentUserIdArgumentResolver.class
})
class AdminReportControllerTest {

    @TestConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
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
    private ReportService reportService;

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

    @BeforeEach
    void setUp() throws Exception {
        customUserDetails = new CustomUserDetails(1L, "admin@example.com", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));

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
    @DisplayName("getReports_returnsSuccess")
    void getReports_returnsSuccess() throws Exception {
        ReportResponse response = ReportResponse.builder().build();
        Page<ReportResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1);
        when(reportService.getReports(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/reports")
                        .param("page", "0")
                        .param("size", "20")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    @DisplayName("getReports_forwardsFilterParams")
    void getReports_forwardsFilterParams() throws Exception {
        Page<ReportResponse> page =
                new PageImpl<>(List.of(ReportResponse.builder().build()), PageRequest.of(0, 20), 1);
        when(reportService.getReports(eq("PENDING"), eq("POST"), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/reports")
                        .param("status", "PENDING")
                        .param("targetType", "POST")
                        .param("page", "0")
                        .param("size", "20")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("getReports_invalidTargetType_returnsInvalidTarget")
    void getReports_invalidTargetType_returnsInvalidTarget() throws Exception {
        when(reportService.getReports(any(), eq("article"), any()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_TARGET));

        mockMvc.perform(get("/api/v1/admin/reports")
                        .param("targetType", "article")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_TARGET.getCode()));
    }

    @Test
    @DisplayName("getReports_rejectsNonSuperAdmin")
    void getReports_rejectsNonSuperAdmin() throws Exception {
        CustomUserDetails adminUserDetails = new CustomUserDetails(2L, "admin@example.com", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mockMvc.perform(get("/api/v1/admin/reports")
                        .with(user(adminUserDetails))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(reportService, never()).getReports(any(), any(), any());
    }

    @Test
    @DisplayName("processReport_returnsSuccess")
    void processReport_returnsSuccess() throws Exception {
        ReportProcessRequest request = new ReportProcessRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "status", ReportStatus.RESOLVED);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "remark", "Processed");

        ReportResponse response = ReportResponse.builder()
                .processorUserId(1L)
                .build();
        when(reportService.processReport(eq(1L), eq(1L), anyString(), anyString())).thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/reports/{reportId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.processorUserId").value(1));
    }

    @Test
    @DisplayName("processReport_rejectsNonSuperAdmin")
    void processReport_rejectsNonSuperAdmin() throws Exception {
        CustomUserDetails adminUserDetails = new CustomUserDetails(2L, "admin@example.com", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mockMvc.perform(put("/api/v1/admin/reports/{reportId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "RESOLVED",
                                  "remark": "Processed"
                                }
                                """)
                        .with(user(adminUserDetails))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(reportService, never()).processReport(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("processReport_normalizesLowercaseStatus")
    void processReport_normalizesLowercaseStatus() throws Exception {
        when(reportService.processReport(eq(1L), eq(1L), anyString(), anyString()))
                .thenReturn(ReportResponse.builder().reportId(1L).build());

        mockMvc.perform(put("/api/v1/admin/reports/{reportId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "resolved",
                                  "remark": "Processed"
                                }
                                """)
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(reportService).processReport(1L, 1L, "RESOLVED", "Processed");
    }

    @Test
    @DisplayName("processReport_rejectsInvalidStatus")
    void processReport_rejectsInvalidStatus() throws Exception {
        mockMvc.perform(put("/api/v1/admin/reports/{reportId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "APPROVED",
                                  "remark": "Processed"
                                }
                                """)
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(reportService, never()).processReport(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("processReport_rejectsBlankStatus")
    void processReport_rejectsBlankStatus() throws Exception {
        mockMvc.perform(put("/api/v1/admin/reports/{reportId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": " ",
                                  "remark": "Processed"
                                }
                                """)
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(reportService, never()).processReport(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("processReport_rejectsUnreadableStatusBody")
    void processReport_rejectsUnreadableStatusBody() throws Exception {
        mockMvc.perform(put("/api/v1/admin/reports/{reportId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": {}
                                }
                                """)
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        verify(reportService, never()).processReport(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("processReport_rejectsPendingStatus")
    void processReport_rejectsPendingStatus() throws Exception {
        mockMvc.perform(put("/api/v1/admin/reports/{reportId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "PENDING",
                                  "remark": "Processed"
                                }
                                """)
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(reportService, never()).processReport(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("processReport_rejectsOverlongRemark")
    void processReport_rejectsOverlongRemark() throws Exception {
        mockMvc.perform(put("/api/v1/admin/reports/{reportId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "RESOLVED",
                                  "remark": "%s"
                                }
                                """.formatted("a".repeat(256)))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(reportService, never()).processReport(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("processReport_returnsConflictWhenAlreadyProcessed")
    void processReport_returnsConflictWhenAlreadyProcessed() throws Exception {
        ReportProcessRequest request = new ReportProcessRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "status", ReportStatus.RESOLVED);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "remark", "Processed");

        when(reportService.processReport(eq(1L), eq(1L), anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.REPORT_ALREADY_PROCESSED));

        mockMvc.perform(put("/api/v1/admin/reports/{reportId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.REPORT_ALREADY_PROCESSED.getCode()));
    }
}
