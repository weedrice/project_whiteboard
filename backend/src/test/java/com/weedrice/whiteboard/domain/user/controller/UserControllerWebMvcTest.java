package com.weedrice.whiteboard.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.agent.service.AgentService;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.dto.NotificationSettingResponse;
import com.weedrice.whiteboard.domain.user.dto.UpdateNotificationSettingItem;
import com.weedrice.whiteboard.domain.user.dto.UpdateNotificationSettingsRequest;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.domain.user.service.UserService;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

    @MockBean
    private UserService userService;

    @MockBean
    private UserSettingsService userSettingsService;

    @MockBean
    private UserBlockService userBlockService;

    @MockBean
    private BoardService boardService;

    @MockBean
    private PostService postService;

    @MockBean
    private CommentService commentService;

    @MockBean
    private AgentService agentService;

    @MockBean
    private org.springframework.context.MessageSource messageSource;

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
    @DisplayName("bulk 알림 설정 수정 요청 성공")
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
    @DisplayName("bulk 알림 설정 수정 요청 실패 - 빈 목록")
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
    @DisplayName("bulk 알림 설정 수정 요청 실패 - 잘못된 항목")
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
