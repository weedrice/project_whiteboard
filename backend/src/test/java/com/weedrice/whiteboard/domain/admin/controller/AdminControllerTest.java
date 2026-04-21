package com.weedrice.whiteboard.domain.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.admin.dto.AdminCreateRequest;
import com.weedrice.whiteboard.domain.admin.dto.AdminResponse;
import com.weedrice.whiteboard.domain.admin.dto.BoardManagerUpdateRequest;
import com.weedrice.whiteboard.domain.admin.dto.DashboardStatsDto;
import com.weedrice.whiteboard.domain.admin.dto.IpBlockResponse;
import com.weedrice.whiteboard.domain.admin.dto.SuperAdminRequest;
import com.weedrice.whiteboard.domain.admin.dto.SuperAdminResponse;
import com.weedrice.whiteboard.domain.admin.dto.SuperAdminUpdateResponse;
import com.weedrice.whiteboard.domain.admin.entity.AdminRole;
import com.weedrice.whiteboard.domain.admin.interceptor.IpBlockInterceptor;
import com.weedrice.whiteboard.domain.admin.service.AdminAssignmentService;
import com.weedrice.whiteboard.domain.admin.service.AdminDashboardService;
import com.weedrice.whiteboard.domain.admin.service.AdminReadService;
import com.weedrice.whiteboard.domain.admin.service.IpBlockService;
import com.weedrice.whiteboard.domain.admin.service.SuperAdminService;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.global.config.SecurityConfig;
import com.weedrice.whiteboard.global.config.WebConfig;
import com.weedrice.whiteboard.global.ratelimit.RateLimitInterceptor;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.JwtAuthenticationEntryPoint;
import com.weedrice.whiteboard.global.security.JwtAuthenticationFilter;
import com.weedrice.whiteboard.global.security.RefererCheckInterceptor;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        })
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminAssignmentService adminAssignmentService;

    @MockBean
    private AdminReadService adminReadService;

    @MockBean
    private AdminDashboardService adminDashboardService;

    @MockBean
    private SuperAdminService superAdminService;

    @MockBean
    private IpBlockService ipBlockService;

    @MockBean
    private PostService postService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockBean
    private IpBlockInterceptor ipBlockInterceptor;

    @MockBean
    private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockBean
    private RefererCheckInterceptor refererCheckInterceptor;

    @MockBean
    private RateLimitInterceptor rateLimitInterceptor;

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
        SuperAdminResponse response = SuperAdminResponse.builder().loginId("super").build();
        when(superAdminService.getSuperAdmin()).thenReturn(List.of(response));

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
        SuperAdminRequest request = new SuperAdminRequest();
        ReflectionTestUtils.setField(request, "loginId", "admin");

        SuperAdminUpdateResponse response = SuperAdminUpdateResponse.builder().loginId("admin").isSuperAdmin(true)
                .build();
        when(superAdminService.createSuperAdmin("admin")).thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/super/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.loginId").value("admin"));
    }

    @Test
    @DisplayName("관리자 목록 페이지 조회 성공")
    void getAllAdmins_returnsPagedResponse() throws Exception {
        AdminResponse response = AdminResponse.builder().adminId(1L).role("BOARD_ADMIN").isActive(true).build();
        when(adminReadService.getAllAdmins(any())).thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/admin/admins")
                        .param("page", "0")
                        .param("size", "20")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].adminId").value(1L));
    }

    @Test
    @DisplayName("관리자 생성 성공")
    void createAdmin_returnsSuccess() throws Exception {
        AdminCreateRequest request = new AdminCreateRequest();
        ReflectionTestUtils.setField(request, "loginId", "admin");
        ReflectionTestUtils.setField(request, "boardId", 1L);
        ReflectionTestUtils.setField(request, "role", AdminRole.BOARD_ADMIN);

        AdminResponse response = AdminResponse.builder().adminId(1L).role("BOARD_ADMIN").isActive(true).build();
        when(adminAssignmentService.createAdmin("admin", 1L, AdminRole.BOARD_ADMIN)).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/admins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.adminId").value(1L));
    }

    @Test
    @DisplayName("지원하지 않는 role 로 관리자 생성 요청 시 400을 반환한다")
    void createAdmin_returnsBadRequestWhenRoleIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/admin/admins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "admin",
                                  "boardId": 1,
                                  "role": "SUPER_ADMIN"
                                }
                                """)
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("게시판 관리자 조회 성공")
    void getBoardManager_returnsSuccess() throws Exception {
        AdminResponse response = AdminResponse.builder().adminId(1L).role("BOARD_ADMIN").isActive(true).build();
        when(adminAssignmentService.getBoardManager(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/boards/{boardId}/manager", 1L)
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.adminId").value(1L));
    }

    @Test
    @DisplayName("게시판 관리자 교체 성공")
    void replaceBoardManager_returnsSuccess() throws Exception {
        BoardManagerUpdateRequest request = new BoardManagerUpdateRequest();
        ReflectionTestUtils.setField(request, "loginId", "newadmin");

        AdminResponse response = AdminResponse.builder().adminId(1L).role("BOARD_ADMIN").isActive(true).build();
        when(adminAssignmentService.replaceBoardManager(1L, "newadmin")).thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/boards/{boardId}/manager", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.adminId").value(1L));
    }

    @Test
    @DisplayName("차단 IP 목록 페이지 조회 성공")
    void getBlockedIps_returnsSuccess() throws Exception {
        IpBlockResponse response = IpBlockResponse.builder().ipAddress("1.1.1.1").build();
        when(ipBlockService.getBlockedIps(any())).thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/admin/ip-blocks")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].ipAddress").value("1.1.1.1"));
    }

    @Test
    @DisplayName("대시보드 통계 조회 성공")
    void getDashboardStats_returnsSuccess() throws Exception {
        DashboardStatsDto response = DashboardStatsDto.builder().totalUsers(10L).build();
        when(adminDashboardService.getDashboardStats()).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/stats")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUsers").value(10L));
    }
}
