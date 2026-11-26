package com.weedrice.whiteboard.domain.ad.controller;

import com.weedrice.whiteboard.domain.ad.dto.AdResponse;
import com.weedrice.whiteboard.domain.ad.service.AdService;
import com.weedrice.whiteboard.domain.admin.interceptor.IpBlockInterceptor;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.config.CurrentUserIdWebMvcConfig;
import com.weedrice.whiteboard.global.config.SecurityConfig;
import com.weedrice.whiteboard.global.config.WebConfig;
import com.weedrice.whiteboard.global.common.util.ClientIpResolver;
import com.weedrice.whiteboard.global.ratelimit.RateLimitInterceptor;
import com.weedrice.whiteboard.global.ratelimit.CounterEventGuard;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.CurrentUserIdArgumentResolver;
import com.weedrice.whiteboard.global.security.JwtAuthenticationEntryPoint;
import com.weedrice.whiteboard.global.security.JwtAuthenticationFilter;
import com.weedrice.whiteboard.global.security.RefererCheckInterceptor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
    },
    excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class
    })
@Import({
        CurrentUserIdWebMvcConfig.class,
        CurrentUserIdArgumentResolver.class,
        CounterEventGuard.class
})
class AdControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdService adService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockitoBean
    private IpBlockInterceptor ipBlockInterceptor;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean
    private RefererCheckInterceptor refererCheckInterceptor;

    @MockitoBean
    private RateLimitInterceptor rateLimitInterceptor;

    @MockitoBean
    private ClientIpResolver clientIpResolver;

    private CustomUserDetails customUserDetails;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void setUp() throws Exception {
        customUserDetails = new CustomUserDetails(1L, "test@example.com", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        when(ipBlockInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(refererCheckInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(rateLimitInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.10");

        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            HttpServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

    }

    @Test
    @DisplayName("광고 조회 성공")
    void getAd_returnsSuccess() throws Exception {
        AdResponse response = AdResponse.builder().build();
        when(adService.getAdResponse(eq("TOP"))).thenReturn(response);

        mockMvc.perform(get("/api/v1/ads")
                        .param("placement", "TOP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("광고 impression 기록 성공")
    void recordAdImpression_returnsSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/ads/{adId}/impression", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adService).recordAdImpression(1L);
    }

    @Test
    void recordAdImpression_skipsServiceWhenRecentlyRecorded() throws Exception {
        mockMvc.perform(post("/api/v1/ads/{adId}/impression", 2L))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ads/{adId}/impression", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adService).recordAdImpression(2L);
    }

    @Test
    @DisplayName("비활성 광고 impression 기록은 404를 반환한다")
    void recordAdImpression_returnsNotFoundWhenAdInactive() throws Exception {
        doThrow(new BusinessException(ErrorCode.AD_NOT_FOUND))
                .when(adService)
                .recordAdImpression(eq(3L));

        mockMvc.perform(post("/api/v1/ads/{adId}/impression", 3L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.AD_NOT_FOUND.getCode()));

        mockMvc.perform(post("/api/v1/ads/{adId}/impression", 3L))
                .andExpect(status().isNotFound());
        verify(adService, times(2)).recordAdImpression(3L);
    }

    @Test
    @DisplayName("광고 클릭 기록 성공")
    void recordAdClick_returnsSuccess() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                customUserDetails, null, customUserDetails.getAuthorities()));
        when(adService.recordAdClick(eq(1L), eq(1L), eq("203.0.113.10"))).thenReturn("http://example.com");

        mockMvc.perform(post("/api/v1/ads/{adId}/click", 1L)
                        .header("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
                        .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());

        verify(adService).recordAdClick(1L, 1L, "203.0.113.10");
    }

    @Test
    void recordAdClick_returnsTargetUrlWithoutIncrementWhenRecentlyRecorded() throws Exception {
        when(adService.recordAdClick(2L, null, "203.0.113.10"))
                .thenThrow(new BusinessException(ErrorCode.AD_NOT_FOUND))
                .thenReturn("http://example.com");
        when(adService.getActiveAdTargetUrl(2L)).thenReturn("http://example.com/updated");

        mockMvc.perform(post("/api/v1/ads/{adId}/click", 2L))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/ads/{adId}/click", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("http://example.com"));
        mockMvc.perform(post("/api/v1/ads/{adId}/click", 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("http://example.com/updated"));

        verify(adService, times(2)).recordAdClick(2L, null, "203.0.113.10");
        verify(adService).getActiveAdTargetUrl(2L);
    }

    @Test
    @DisplayName("인증 없는 광고 클릭은 userId 없이 서비스에 위임한다")
    void recordAdClick_withoutAuthentication_passesNullUserId() throws Exception {
        when(adService.recordAdClick(eq(1L), isNull(), eq("203.0.113.10"))).thenReturn("http://example.com");

        mockMvc.perform(post("/api/v1/ads/{adId}/click", 1L)
                        .header("X-Forwarded-For", "203.0.113.10, 10.0.0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());

        verify(adService).recordAdClick(eq(1L), isNull(), eq("203.0.113.10"));
    }

    @Test
    @DisplayName("비활성 광고 click 기록은 404를 반환한다")
    void recordAdClick_returnsNotFoundWhenAdInactive() throws Exception {
        when(adService.recordAdClick(eq(3L), any(), nullable(String.class)))
                .thenThrow(new BusinessException(ErrorCode.AD_NOT_FOUND));

        mockMvc.perform(post("/api/v1/ads/{adId}/click", 3L)
                        .with(user(customUserDetails)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.AD_NOT_FOUND.getCode()));
    }
}
