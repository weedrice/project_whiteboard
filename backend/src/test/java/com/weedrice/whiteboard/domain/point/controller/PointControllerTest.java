package com.weedrice.whiteboard.domain.point.controller;

import com.weedrice.whiteboard.domain.point.dto.PointHistoryResponse;
import com.weedrice.whiteboard.domain.point.dto.UserPointResponse;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.global.config.CurrentUserIdWebMvcConfig;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CurrentUserIdArgumentResolver;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import static org.mockito.Mockito.doAnswer;

@WebMvcTest(controllers = PointController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    })
@Import({
        CurrentUserIdWebMvcConfig.class,
        CurrentUserIdArgumentResolver.class
})
class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PointService pointService;

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
    @DisplayName("내 포인트 조회 성공")
    void getMyPoints_returnsSuccess() throws Exception {
        // given
        UserPointResponse response = UserPointResponse.builder().build();
        when(pointService.getUserPoint(eq(1L))).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/points/me")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("내 포인트 히스토리 조회 성공")
    void getMyPointHistories_returnsSuccess() throws Exception {
        // given
        PointHistoryResponse response = PointHistoryResponse.builder().build();
        when(pointService.getPointHistories(eq(1L), any(), any())).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/points/me/history")
                        .param("page", "0")
                        .param("size", "20")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("내 포인트 히스토리 조회는 페이지 크기를 최대 100으로 제한한다")
    void getMyPointHistories_clampsLargePageSize() throws Exception {
        PointHistoryResponse response = PointHistoryResponse.builder().build();
        when(pointService.getPointHistories(eq(1L), any(), any())).thenReturn(response);
        org.mockito.Mockito.clearInvocations(pointService);

        mockMvc.perform(get("/api/v1/points/me/history")
                        .param("page", "0")
                        .param("size", "1000")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(pointService).getPointHistories(eq(1L), any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("내 포인트 히스토리 조회는 잘못된 페이지 크기를 400으로 처리한다")
    void getMyPointHistories_invalidSize_returnsBadRequest() throws Exception {
        org.mockito.Mockito.clearInvocations(pointService);

        mockMvc.perform(get("/api/v1/points/me/history")
                        .param("page", "0")
                        .param("size", "-1")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(pointService, never()).getPointHistories(anyLong(), any(), any());
    }

    @Test
    @DisplayName("내 포인트 히스토리 조회는 지원하지 않는 타입을 400으로 처리한다")
    void getMyPointHistories_invalidType_returnsBadRequest() throws Exception {
        when(pointService.getPointHistories(eq(1L), eq("bonus"), any()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        mockMvc.perform(get("/api/v1/points/me/history")
                        .param("type", "bonus")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
    }
}
