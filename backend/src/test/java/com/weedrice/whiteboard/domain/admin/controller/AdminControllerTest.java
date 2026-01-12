package com.weedrice.whiteboard.domain.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.admin.dto.*;
import com.weedrice.whiteboard.domain.admin.service.AdminService;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import static org.mockito.Mockito.doAnswer;

@WebMvcTest(controllers = AdminController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    })
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminService adminService;

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
    @DisplayName("Super Admin 조회 성공")
    void getSuperAdmin_returnsSuccess() throws Exception {
        // given
        SuperAdminResponse response = SuperAdminResponse.builder().build();
        when(adminService.getSuperAdmin()).thenReturn(List.of(response));

        // when & then
        mockMvc.perform(get("/api/v1/admin/super")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("Super Admin 활성화 성공")
    void activeSuperAdmin_returnsSuccess() throws Exception {
        // given
        SuperAdminRequest request = new SuperAdminRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "loginId", "admin");
        
        SuperAdminUpdateResponse response = SuperAdminUpdateResponse.builder().build();
        when(adminService.createSuperAdmin(eq("admin"))).thenReturn(response);

        // when & then
        mockMvc.perform(put("/api/v1/admin/super/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("모든 Admin 조회 성공")
    void getAllAdmins_returnsSuccess() throws Exception {
        // given
        AdminResponse response = AdminResponse.builder().build();
        when(adminService.getAllAdmins()).thenReturn(List.of(response));

        // when & then
        mockMvc.perform(get("/api/v1/admin/admins")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("Admin 생성 성공")
    void createAdmin_returnsSuccess() throws Exception {
        // given
        AdminCreateRequest request = new AdminCreateRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "loginId", "admin");
        org.springframework.test.util.ReflectionTestUtils.setField(request, "boardId", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "role", "ADMIN");
        
        AdminResponse response = AdminResponse.builder().build();
        when(adminService.createAdmin(eq("admin"), eq(1L), anyString())).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/admin/admins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("차단된 IP 목록 조회 성공")
    void getBlockedIps_returnsSuccess() throws Exception {
        // given
        IpBlockResponse response = IpBlockResponse.builder().build();
        when(adminService.getBlockedIps()).thenReturn(List.of(response));

        // when & then
        mockMvc.perform(get("/api/v1/admin/ip-blocks")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("대시보드 통계 조회 성공")
    void getDashboardStats_returnsSuccess() throws Exception {
        // given
        DashboardStatsDto response = DashboardStatsDto.builder().build();
        when(adminService.getDashboardStats()).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/admin/stats")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
