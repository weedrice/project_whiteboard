package com.weedrice.whiteboard.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.agent.service.AgentLifecycleService;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.dto.NotificationSettingResponse;
import com.weedrice.whiteboard.domain.user.dto.UpdateNotificationSettingItem;
import com.weedrice.whiteboard.domain.user.dto.UpdateNotificationSettingsRequest;
import com.weedrice.whiteboard.domain.user.dto.UserProfileResponse;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.domain.user.service.UserProfileService;
import com.weedrice.whiteboard.domain.user.service.UserSecurityService;
import com.weedrice.whiteboard.domain.user.service.UserSettingsService;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    })
class UserControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserProfileService userProfileService;

    @MockitoBean
    private UserSecurityService userSecurityService;

    @MockitoBean
    private UserSettingsService userSettingsService;

    @MockitoBean
    private UserBlockService userBlockService;

    @MockitoBean
    private BoardService boardService;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private CommentService commentService;

    @MockitoBean
    private AgentLifecycleService agentLifecycleService;

    @MockitoBean
    private org.springframework.context.MessageSource messageSource;

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
        customUserDetails = new CustomUserDetails(
                1L,
                "testuser",
                "encodedPassword",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        when(ipBlockInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(refererCheckInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(rateLimitInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            HttpServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("Public user profile response excludes private fields")
    void getUserProfile_returnsOnlyPublicFields() throws Exception {
        when(userProfileService.getUserProfile(1L)).thenReturn(UserProfileResponse.builder()
                .userId(1L)
                .displayName("tester")
                .profileImageUrl("/files/1")
                .createdAt(java.time.LocalDateTime.parse("2026-04-17T10:25:22"))
                .postCount(5L)
                .commentCount(7L)
                .build());

        mockMvc.perform(get("/api/v1/users/{userId}", 1L)
                        .with(user(customUserDetails))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.displayName").value("tester"))
                .andExpect(jsonPath("$.data.profileImageUrl").value("/files/1"))
                .andExpect(jsonPath("$.data.postCount").value(5))
                .andExpect(jsonPath("$.data.commentCount").value(7))
                .andExpect(jsonPath("$.data.loginId").doesNotExist())
                .andExpect(jsonPath("$.data.lastLoginAt").doesNotExist());
    }

    @Test
    @DisplayName("Bulk notification settings update request succeeds")
    void updateNotificationSettingsBulk_returnsSuccess() throws Exception {
        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest();
        request.setSettings(List.of(
                new UpdateNotificationSettingItem("LIKE", true),
                new UpdateNotificationSettingItem("COMMENT", false),
                new UpdateNotificationSettingItem("REPLY", true)));

        when(userSettingsService.updateNotificationSettings(any(), anyList()))
                .thenReturn(List.of(
                        new NotificationSettingResponse("LIKE", true),
                        new NotificationSettingResponse("COMMENT", false),
                        new NotificationSettingResponse("REPLY", true)));

        mockMvc.perform(put("/api/v1/users/me/notification-settings/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].notificationType").value("LIKE"))
                .andExpect(jsonPath("$.data[1].isEnabled").value(false));
    }

    @Test
    @DisplayName("Bulk notification settings update request fails for empty list")
    void updateNotificationSettingsBulk_emptySettings_returnsBadRequest() throws Exception {
        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest();
        request.setSettings(List.of());

        mockMvc.perform(put("/api/v1/users/me/notification-settings/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Bulk notification settings update request fails for invalid item")
    void updateNotificationSettingsBulk_invalidItem_returnsBadRequest() throws Exception {
        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest();
        request.setSettings(List.of(
                new UpdateNotificationSettingItem(" ", true),
                new UpdateNotificationSettingItem("COMMENT", null)));

        mockMvc.perform(put("/api/v1/users/me/notification-settings/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

}
