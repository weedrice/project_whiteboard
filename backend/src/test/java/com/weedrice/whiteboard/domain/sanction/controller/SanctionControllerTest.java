package com.weedrice.whiteboard.domain.sanction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.sanction.dto.SanctionCreateRequest;
import com.weedrice.whiteboard.domain.sanction.dto.SanctionResponse;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SanctionController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    })
@AutoConfigureMockMvc
@Import(SanctionControllerTest.TestSecurityConfig.class)
class SanctionControllerTest {

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
    private SanctionService sanctionService;

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
    @DisplayName("제재 생성 성공")
    void createSanction_returnsSuccess() throws Exception {
        // given
        SanctionCreateRequest request = new SanctionCreateRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "targetUserId", 2L);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "type", "WARNING");
        org.springframework.test.util.ReflectionTestUtils.setField(request, "remark", "Test sanction");
        org.springframework.test.util.ReflectionTestUtils.setField(request, "endDate", LocalDateTime.now().plusDays(7));
        org.springframework.test.util.ReflectionTestUtils.setField(request, "contentId", 100L);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "contentType", "POST");
        
        when(sanctionService.createSanction(any(), eq(2L), anyString(), anyString(), any(), eq(100L), eq("POST")))
                .thenReturn(1L);

        // when & then
        mockMvc.perform(post("/api/v1/admin/sanctions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(1L));
    }

    @Test
    @DisplayName("createSanction rejects overlong remark")
    void createSanction_rejectsOverlongRemark() throws Exception {
        SanctionCreateRequest request = new SanctionCreateRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "targetUserId", 2L);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "type", "WARNING");
        org.springframework.test.util.ReflectionTestUtils.setField(request, "remark", "a".repeat(256));

        mockMvc.perform(post("/api/v1/admin/sanctions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails)))
                .andExpect(status().isBadRequest());

        verify(sanctionService, never()).createSanction(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("제재 목록 조회 성공")
    void getSanctions_returnsSuccess() throws Exception {
        // given
        SanctionResponse response = SanctionResponse.builder().build();
        Page<SanctionResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1);
        when(sanctionService.getSanctions(any(), any())).thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/admin/sanctions")
                        .param("page", "0")
                        .param("size", "20")
                        .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("제재 목록 조회는 페이지 크기를 최대 100으로 제한한다")
    void getSanctions_clampsLargePageSize() throws Exception {
        Page<SanctionResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);
        when(sanctionService.getSanctions(any(), any())).thenReturn(page);
        org.mockito.Mockito.clearInvocations(sanctionService);

        mockMvc.perform(get("/api/v1/admin/sanctions")
                        .param("page", "0")
                        .param("size", "1000")
                        .with(user(customUserDetails)))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(sanctionService).getSanctions(any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("제재 목록 조회는 잘못된 페이지 파라미터를 400으로 처리한다")
    void getSanctions_invalidPage_returnsBadRequest() throws Exception {
        org.mockito.Mockito.clearInvocations(sanctionService);

        mockMvc.perform(get("/api/v1/admin/sanctions")
                        .param("page", "-1")
                        .param("size", "20")
                        .with(user(customUserDetails)))
                .andExpect(status().isBadRequest());

        verify(sanctionService, never()).getSanctions(any(), any());
    }

    @Test
    @DisplayName("get sanctions rejects non-super-admin users")
    void getSanctions_rejectsNonSuperAdmin() throws Exception {
        CustomUserDetails adminUserDetails = new CustomUserDetails(2L, "admin@example.com", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mockMvc.perform(get("/api/v1/admin/sanctions")
                        .with(user(adminUserDetails)))
                .andExpect(status().isForbidden());

        verify(sanctionService, never()).getSanctions(any(), any());
    }

    @Test
    @DisplayName("create sanctions rejects non-super-admin users")
    void createSanction_rejectsNonSuperAdmin() throws Exception {
        CustomUserDetails adminUserDetails = new CustomUserDetails(2L, "admin@example.com", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mockMvc.perform(post("/api/v1/admin/sanctions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": 2,
                                  "type": "WARNING",
                                  "remark": "Test sanction"
                                }
                                """)
                        .with(user(adminUserDetails)))
                .andExpect(status().isForbidden());

        verify(sanctionService, never()).createSanction(any(), any(), any(), any(), any(), any(), any());
    }
}
