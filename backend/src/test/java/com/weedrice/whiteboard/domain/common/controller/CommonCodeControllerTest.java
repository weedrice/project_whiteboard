package com.weedrice.whiteboard.domain.common.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.common.dto.CommonCodeDetailRequest;
import com.weedrice.whiteboard.domain.common.dto.CommonCodeDetailResponse;
import com.weedrice.whiteboard.domain.common.dto.CommonCodeRequest;
import com.weedrice.whiteboard.domain.common.dto.CommonCodeResponse;
import com.weedrice.whiteboard.domain.common.service.CommonCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import static org.mockito.Mockito.doAnswer;

@WebMvcTest(controllers = CommonCodeController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    })
class CommonCodeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CommonCodeService commonCodeService;

    @MockBean
    private com.weedrice.whiteboard.global.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.weedrice.whiteboard.global.security.JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockBean
    private com.weedrice.whiteboard.domain.admin.interceptor.IpBlockInterceptor ipBlockInterceptor;

    @MockBean
    private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockBean
    private com.weedrice.whiteboard.global.security.RefererCheckInterceptor refererCheckInterceptor;

    @MockBean
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
        mockMvc.perform(get("/api/v1/common-codes")
                        .with(csrf()))
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
        mockMvc.perform(get("/api/v1/common-codes/{typeCode}", typeCode)
                        .with(csrf()))
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
        mockMvc.perform(get("/api/v1/common-codes/{typeCode}/details", typeCode)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}
