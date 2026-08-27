package com.weedrice.whiteboard.domain.common.controller;

import tools.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.common.dto.CommonCodeDetailRequest;
import com.weedrice.whiteboard.domain.common.dto.CommonCodeDetailResponse;
import com.weedrice.whiteboard.domain.common.dto.CommonCodeRequest;
import com.weedrice.whiteboard.domain.common.dto.CommonCodeResponse;
import com.weedrice.whiteboard.domain.common.service.CommonCodeService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CommonCodeController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    },
    excludeAutoConfiguration = {
        org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration.class,
        org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration.class,
        org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration.class,
        org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration.class
    })
class CommonCodeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CommonCodeService commonCodeService;

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
    @DisplayName("공통 코드 목록 조회 성공")
    void getAllCommonCodes_returnsSuccess() throws Exception {
        // given
        CommonCodeResponse response = new CommonCodeResponse("TEST", "Test Code", "Description", null, null);
        when(commonCodeService.getAllCommonCodes()).thenReturn(List.of(response));

        // when & then
        mockMvc.perform(get("/api/v1/common-codes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("공통 코드 상세 조회 성공")
    void getCommonCode_returnsSuccess() throws Exception {
        // given
        String typeCode = "TEST";
        CommonCodeResponse response = new CommonCodeResponse("TEST", "Test Code", "Description", null, null);
        when(commonCodeService.getCommonCode(eq(typeCode))).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/common-codes/{typeCode}", typeCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("공통 코드 상세 목록 조회 성공")
    void getCommonCodeDetails_returnsSuccess() throws Exception {
        // given
        String typeCode = "TEST";
        CommonCodeDetailResponse response = new CommonCodeDetailResponse(1L, "TEST", "VALUE", "Name", 0, true);
        when(commonCodeService.getCommonCodeDetails(eq(typeCode))).thenReturn(List.of(response));

        // when & then
        mockMvc.perform(get("/api/v1/common-codes/{typeCode}/details", typeCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("관리자 공통 코드 상세 목록은 비활성 코드를 포함해 반환한다")
    void getAllCommonCodeDetails_returnsSuccess() throws Exception {
        String typeCode = "TEST";
        CommonCodeDetailResponse inactive = new CommonCodeDetailResponse(
                2L, typeCode, "INACTIVE", "Inactive", 20, false);
        when(commonCodeService.getAllCommonCodeDetails(typeCode)).thenReturn(List.of(inactive));

        mockMvc.perform(get("/api/v1/common-codes/{typeCode}/details/all", typeCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].isActive").value(false));
    }

    @Test
    @DisplayName("없는 공통 코드 상세 목록 조회는 404를 반환한다")
    void getCommonCodeDetails_returnsNotFoundWhenTypeMissing() throws Exception {
        String typeCode = "MISSING";
        when(commonCodeService.getCommonCodeDetails(eq(typeCode)))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/v1/common-codes/{typeCode}/details", typeCode))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("공통 코드 생성 충돌은 409를 반환한다")
    void createCommonCode_returnsConflictWhenDuplicate() throws Exception {
        CommonCodeRequest request = new CommonCodeRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "typeCode", "TEST");
        org.springframework.test.util.ReflectionTestUtils.setField(request, "typeName", "Test Code");

        when(commonCodeService.createCommonCode(any(CommonCodeRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.DUPLICATE_RESOURCE));

        mockMvc.perform(post("/api/v1/common-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.DUPLICATE_RESOURCE.getCode()));
    }

    @Test
    @DisplayName("공통 코드 설명이 255자를 초과하면 400을 반환한다")
    void createCommonCode_descriptionTooLong_returnsBadRequest() throws Exception {
        Map<String, Object> request = Map.of(
                "typeCode", "TEST",
                "typeName", "Test Code",
                "description", "a".repeat(256));

        mockMvc.perform(post("/api/v1/common-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        verify(commonCodeService, never()).createCommonCode(any(CommonCodeRequest.class));
    }

    @Test
    @DisplayName("공통 코드 설명은 검증 전에 trim 된다")
    void createCommonCode_trimsDescriptionBeforeValidation() throws Exception {
        String description = "a".repeat(255);
        Map<String, Object> request = Map.of(
                "typeCode", "TEST",
                "typeName", "Test Code",
                "description", "  " + description + "  ");
        CommonCodeResponse response = new CommonCodeResponse("TEST", "Test Code", description, null, null);
        when(commonCodeService.createCommonCode(any(CommonCodeRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/common-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(commonCodeService).createCommonCode(argThat(commonCodeRequest ->
                description.equals(commonCodeRequest.getDescription())));
    }

    @Test
    @DisplayName("공통 코드 상세 생성 충돌은 409를 반환한다")
    void createCommonCodeDetail_returnsConflictWhenDuplicate() throws Exception {
        CommonCodeDetailRequest request = new CommonCodeDetailRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "codeValue", "VALUE");
        org.springframework.test.util.ReflectionTestUtils.setField(request, "codeName", "Value");
        org.springframework.test.util.ReflectionTestUtils.setField(request, "sortOrder", 1);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "isActive", true);

        when(commonCodeService.createCommonCodeDetail(eq("TEST"), any(CommonCodeDetailRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.DUPLICATE_RESOURCE));

        mockMvc.perform(post("/api/v1/common-codes/{typeCode}/details", "TEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.DUPLICATE_RESOURCE.getCode()));
    }

    @Test
    @DisplayName("management endpoints require SUPER_ADMIN")
    void managementEndpoints_requireSuperAdminPreAuthorize() throws Exception {
        assertRequiresSuperAdmin("createCommonCode", CommonCodeRequest.class);
        assertRequiresSuperAdmin("getAllCommonCodes");
        assertRequiresSuperAdmin("getCommonCode", String.class);
        assertRequiresSuperAdmin("updateCommonCode", String.class, CommonCodeRequest.class);
        assertRequiresSuperAdmin("createCommonCodeDetail", String.class, CommonCodeDetailRequest.class);
        assertRequiresSuperAdmin("getAllCommonCodeDetails", String.class);
        assertRequiresSuperAdmin("updateCommonCodeDetail", Long.class, CommonCodeDetailRequest.class);
        assertRequiresSuperAdmin("deleteCommonCodeDetail", Long.class);
    }

    @Test
    @DisplayName("public detail endpoint has no PreAuthorize")
    void publicDetailEndpoint_hasNoPreAuthorize() throws Exception {
        Method method = CommonCodeController.class.getDeclaredMethod("getCommonCodeDetails", String.class);

        assertThat(method.getAnnotation(PreAuthorize.class)).isNull();
    }

    private void assertRequiresSuperAdmin(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = CommonCodeController.class.getDeclaredMethod(methodName, parameterTypes);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasRole('SUPER_ADMIN')");
    }
}
