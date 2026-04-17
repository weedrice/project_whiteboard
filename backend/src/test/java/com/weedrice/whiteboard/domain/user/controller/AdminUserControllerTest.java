package com.weedrice.whiteboard.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.user.dto.UserAdminResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserAdminQueryService;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

@WebMvcTest(controllers = AdminUserController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    })
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserAdminQueryService userAdminQueryService;

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
    @DisplayName("사용자 검색 성공")
    void searchUsers_returnsSuccess() throws Exception {
        // given
        UserAdminResponse resp = UserAdminResponse.builder()
                .userId(1L)
                .loginId("login1")
                .email("a@b.com")
                .displayName("user1")
                .status("ACTIVE")
                .role("USER")
                .build();
        Page<UserAdminResponse> responsePage = new PageImpl<>(List.of(resp), PageRequest.of(0, 20), 1);
        when(userAdminQueryService.searchUsersForAdmin(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any())).thenReturn(responsePage);

        // when & then
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("keyword", "test")
                        .param("page", "0")
                        .param("size", "20")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].role").value("USER"));
    }

    @Test
    @DisplayName("사용자 상태 업데이트 성공")
    void updateUserStatus_returnsSuccess() throws Exception {
        // given
        com.weedrice.whiteboard.domain.user.dto.UserStatusUpdateRequest request = 
            new com.weedrice.whiteboard.domain.user.dto.UserStatusUpdateRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "status", "ACTIVE");
        
        doAnswer(invocation -> null).when(userAdminQueryService).updateUserStatus(anyLong(), anyString());

        // when & then
        mockMvc.perform(put("/api/v1/admin/users/{userId}/status", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
