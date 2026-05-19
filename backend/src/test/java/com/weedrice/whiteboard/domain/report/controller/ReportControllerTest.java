package com.weedrice.whiteboard.domain.report.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.report.constant.ReportConstraints;
import com.weedrice.whiteboard.domain.report.dto.MyReportResponse;
import com.weedrice.whiteboard.domain.report.dto.ReportCreateRequest;
import com.weedrice.whiteboard.domain.report.entity.ReportReasonType;
import com.weedrice.whiteboard.domain.report.entity.ReportTargetType;
import com.weedrice.whiteboard.domain.report.service.ReportService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReportController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
        })
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@org.springframework.context.annotation.Import({
        ReportControllerTest.TestSecurityConfig.class,
        com.weedrice.whiteboard.global.exception.GlobalExceptionHandler.class
})
class ReportControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
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

    @MockitoBean
    private org.springframework.context.MessageSource messageSource;

    private CustomUserDetails customUserDetails;

    @BeforeEach
    void setUp() throws Exception {
        customUserDetails = new CustomUserDetails(1L, "test@example.com", "password",
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

    @Test
    @DisplayName("createReport returns created")
    void createReport_success() throws Exception {
        ReportCreateRequest request = new ReportCreateRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "targetType", ReportTargetType.POST);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "targetId", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "reasonType", ReportReasonType.SPAM);

        when(reportService.createReport(any(), any(), any(), any(), any(), any())).thenReturn(1L);

        mockMvc.perform(post("/api/v1/reports")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("reportUser applies DTO validation")
    void reportUser_validationFailure() throws Exception {
        mockMvc.perform(post("/api/v1/reports/users")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"spam\"}"))
                .andExpect(status().isBadRequest());

        verify(reportService, never()).createReport(anyLong(), anyString(), anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("reportUser rejects overlong link")
    void reportUser_rejectsOverlongLink() throws Exception {
        mockMvc.perform(post("/api/v1/reports/users")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": 11,
                                  "reason": "spam user",
                                  "link": "%s"
                                }
                                """.formatted("a".repeat(256))))
                .andExpect(status().isBadRequest());

        verify(reportService, never()).createReport(anyLong(), anyString(), anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("reportPost forwards the provided reasonType")
    void reportPost_usesProvidedReasonType() throws Exception {
        when(reportService.createReport(any(), any(), any(), any(), any(), any())).thenReturn(3L);

        mockMvc.perform(post("/api/v1/reports/posts")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetPostId": 10,
                                  "reason": "spam post",
                                  "reasonType": "spam"
                                }
                                """))
                .andExpect(status().isCreated());

        verify(reportService).createReport(1L, "POST", 10L, "spam", null, "spam post");
    }

    @Test
    @DisplayName("reportPost forwards missing reasonType")
    void reportPost_forwardsMissingReasonType() throws Exception {
        when(reportService.createReport(any(), any(), any(), any(), any(), any())).thenReturn(4L);

        mockMvc.perform(post("/api/v1/reports/posts")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetPostId": 10,
                                  "reason": "spam post"
                                }
                                """))
                .andExpect(status().isCreated());

        verify(reportService).createReport(1L, "POST", 10L, null, null, "spam post");
    }

    @Test
    @DisplayName("reportUser forwards blank reasonType")
    void reportUser_forwardsBlankReasonType() throws Exception {
        when(reportService.createReport(any(), any(), any(), any(), any(), any())).thenReturn(5L);

        mockMvc.perform(post("/api/v1/reports/users")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": 11,
                                  "reason": "spam user",
                                  "reasonType": " "
                                }
                                """))
                .andExpect(status().isCreated());

        verify(reportService).createReport(1L, "USER", 11L, " ", null, "spam user");
    }

    @Test
    @DisplayName("reportComment forwards missing reasonType")
    void reportComment_forwardsMissingReasonType() throws Exception {
        when(reportService.createReport(any(), any(), any(), any(), any(), any())).thenReturn(6L);

        mockMvc.perform(post("/api/v1/reports/comments")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetCommentId": 12,
                                  "reason": "spam comment"
                                }
                                """))
                .andExpect(status().isCreated());

        verify(reportService).createReport(1L, "COMMENT", 12L, null, null, "spam comment");
    }

    @Test
    @DisplayName("createReport accepts lowercase enum values and forwards canonical strings")
    void createReport_normalizesEnumValues() throws Exception {
        when(reportService.createReport(any(), any(), any(), any(), any(), any())).thenReturn(7L);

        mockMvc.perform(post("/api/v1/reports")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType": "post",
                                  "targetId": 1,
                                  "reasonType": "abuse",
                                  "contents": "details"
                                }
                                """))
                .andExpect(status().isCreated());

        verify(reportService).createReport(1L, "POST", 1L, "ABUSE", null, "details");
    }

    @Test
    @DisplayName("createReport rejects invalid enum values")
    void createReport_rejectsInvalidEnumValues() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType": "invalid",
                                  "targetId": 1,
                                  "reasonType": "spam"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        verify(reportService, never()).createReport(anyLong(), anyString(), anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("createReport rejects overlong remark")
    void createReport_rejectsOverlongRemark() throws Exception {
        ReportCreateRequest request = new ReportCreateRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "targetType", ReportTargetType.POST);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "targetId", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "reasonType", ReportReasonType.SPAM);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "remark", "a".repeat(256));

        mockMvc.perform(post("/api/v1/reports")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(reportService, never()).createReport(anyLong(), anyString(), anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("createReport rejects overlong contents")
    void createReport_rejectsOverlongContents() throws Exception {
        ReportCreateRequest request = new ReportCreateRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "targetType", ReportTargetType.POST);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "targetId", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "reasonType", ReportReasonType.SPAM);
        org.springframework.test.util.ReflectionTestUtils.setField(
                request,
                "contents",
                "a".repeat(ReportConstraints.MAX_CONTENTS_LENGTH + 1));

        mockMvc.perform(post("/api/v1/reports")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(reportService, never()).createReport(anyLong(), anyString(), anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("reportUser rejects overlong reason")
    void reportUser_rejectsOverlongReason() throws Exception {
        mockMvc.perform(post("/api/v1/reports/users")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": 11,
                                  "reason": "%s"
                                }
                                """.formatted("a".repeat(ReportConstraints.MAX_CONTENTS_LENGTH + 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(reportService, never()).createReport(anyLong(), anyString(), anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("reportPost rejects overlong reason")
    void reportPost_rejectsOverlongReason() throws Exception {
        mockMvc.perform(post("/api/v1/reports/posts")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetPostId": 10,
                                  "reason": "%s"
                                }
                                """.formatted("a".repeat(ReportConstraints.MAX_CONTENTS_LENGTH + 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(reportService, never()).createReport(anyLong(), anyString(), anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("reportComment rejects overlong reason")
    void reportComment_rejectsOverlongReason() throws Exception {
        mockMvc.perform(post("/api/v1/reports/comments")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetCommentId": 12,
                                  "reason": "%s"
                                }
                                """.formatted("a".repeat(ReportConstraints.MAX_CONTENTS_LENGTH + 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(reportService, never()).createReport(anyLong(), anyString(), anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("reportPost returns a 404 error response for unreadable targets")
    void reportPost_notFound_returnsNotFoundErrorResponse() throws Exception {
        when(reportService.createReport(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.POST_NOT_FOUND));

        mockMvc.perform(post("/api/v1/reports/posts")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetPostId": 10,
                                  "reason": "spam post",
                                  "reasonType": "SPAM"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.POST_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("reportComment returns a 404 error response for unreadable targets")
    void reportComment_notFound_returnsNotFoundErrorResponse() throws Exception {
        when(reportService.createReport(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        mockMvc.perform(post("/api/v1/reports/comments")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetCommentId": 10,
                                  "reason": "spam comment",
                                  "reasonType": "SPAM"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.COMMENT_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("reportComment returns POST_NOT_FOUND when the parent post is unreadable")
    void reportComment_parentPostNotFound_returnsNotFoundErrorResponse() throws Exception {
        when(reportService.createReport(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.POST_NOT_FOUND));

        mockMvc.perform(post("/api/v1/reports/comments")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetCommentId": 10,
                                  "reason": "spam comment",
                                  "reasonType": "SPAM"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.POST_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("getMyReports returns a PageResponse")
    void getMyReports_returnsPageResponse() throws Exception {
        MyReportResponse response = MyReportResponse.builder()
                .reportId(1L)
                .targetType("POST")
                .reasonType("SPAM")
                .status("PENDING")
                .build();
        when(reportService.getMyReports(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/reports/me")
                        .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].targetId").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].targetLoginId").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].adminId").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].processorUserId").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].processedRemark").doesNotExist())
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.page").value(0));
    }

    @Test
    @DisplayName("getMyReports clamps page size to 100")
    void getMyReports_clampsLargePageSize() throws Exception {
        when(reportService.getMyReports(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));
        org.mockito.Mockito.clearInvocations(reportService);

        mockMvc.perform(get("/api/v1/reports/me")
                        .param("page", "0")
                        .param("size", "1000")
                        .with(user(customUserDetails)))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(reportService).getMyReports(eq(1L), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("getMyReports rejects invalid page")
    void getMyReports_invalidPage_returnsBadRequest() throws Exception {
        org.mockito.Mockito.clearInvocations(reportService);

        mockMvc.perform(get("/api/v1/reports/me")
                        .param("page", "-1")
                        .param("size", "20")
                        .with(user(customUserDetails)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        verify(reportService, never()).getMyReports(anyLong(), any());
    }
}
