package com.weedrice.whiteboard.domain.feed.controller;

import com.weedrice.whiteboard.domain.feed.dto.HomeLandingResponse;
import com.weedrice.whiteboard.domain.feed.service.HomeLandingService;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HomeController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    })
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@org.springframework.context.annotation.Import(HomeControllerTest.TestSecurityConfig.class)
class HomeControllerTest {

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

    @MockBean
    private HomeLandingService homeLandingService;

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
    @DisplayName("홈 랜딩 API가 성공 응답을 반환한다")
    void getLanding_returnsSuccess() throws Exception {
        HomeLandingResponse response = HomeLandingResponse.builder()
                .stats(HomeLandingResponse.Stats.builder()
                        .boardCount(1)
                        .postCount(2)
                        .liveCount(1)
                        .onlineCount(10)
                        .build())
                .build();
        when(homeLandingService.getLanding(any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/home/landing")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stats.boardCount").value(1))
                .andExpect(jsonPath("$.data.stats.onlineCount").value(10));

        verify(homeLandingService).getLanding(any(), eq("24h"));
    }
    @Test
    @DisplayName("홈 랜딩 API는 비로그인 사용자도 성공 응답을 받는다")
    void getLanding_allowsAnonymous() throws Exception {
        HomeLandingResponse response = HomeLandingResponse.builder()
                .stats(HomeLandingResponse.Stats.builder()
                        .boardCount(0)
                        .postCount(0)
                        .liveCount(0)
                        .onlineCount(0)
                        .build())
                .build();
        when(homeLandingService.getLanding(any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/home/landing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stats.boardCount").value(0));

        verify(homeLandingService).getLanding(any(), eq("24h"));
    }

    @Test
    @DisplayName("홈 랜딩 API는 요청한 기간 파라미터를 서비스로 전달한다")
    void getLanding_forwardsExplicitPeriod() throws Exception {
        HomeLandingResponse response = HomeLandingResponse.builder()
                .stats(HomeLandingResponse.Stats.builder()
                        .boardCount(3)
                        .postCount(9)
                        .liveCount(2)
                        .onlineCount(7)
                        .build())
                .build();
        when(homeLandingService.getLanding(any(), eq("7d"))).thenReturn(response);

        mockMvc.perform(get("/api/v1/home/landing").param("period", "7d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stats.onlineCount").value(7));

        verify(homeLandingService).getLanding(any(), eq("7d"));
    }
}
