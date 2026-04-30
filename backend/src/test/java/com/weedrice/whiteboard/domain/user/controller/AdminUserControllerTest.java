package com.weedrice.whiteboard.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.user.dto.AdminUserCommentResponse;
import com.weedrice.whiteboard.domain.user.dto.AdminUserPostResponse;
import com.weedrice.whiteboard.domain.user.dto.AdminUserSubscriptionResponse;
import com.weedrice.whiteboard.domain.user.dto.UserAdminResponse;
import com.weedrice.whiteboard.domain.user.dto.UserStatusUpdateRequest;
import com.weedrice.whiteboard.domain.user.service.UserAdminCommandService;
import com.weedrice.whiteboard.domain.user.service.UserAdminQueryService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminUserController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                        classes = com.weedrice.whiteboard.global.config.WebConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                        classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
        })
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserAdminQueryService userAdminQueryService;

    @MockBean
    private UserAdminCommandService userAdminCommandService;

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
                "admin@example.com",
                "password",
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
    @DisplayName("search users returns success")
    void searchUsers_returnsSuccess() throws Exception {
        UserAdminResponse response = UserAdminResponse.builder()
                .userId(1L)
                .loginId("login1")
                .email("a@b.com")
                .displayName("user1")
                .status("ACTIVE")
                .role("USER")
                .build();
        Page<UserAdminResponse> responsePage = new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1);
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
    @DisplayName("search users returns bad request for invalid filters")
    void searchUsers_invalidFiltersReturnsBadRequest() throws Exception {
        doThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE))
                .when(userAdminQueryService).searchUsersForAdmin(
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
                        any());

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("role", "BOARDADMIN")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    @Test
    @DisplayName("get user posts returns admin-specific post response")
    void getUserPosts_returnsAdminSpecificPostResponse() throws Exception {
        AdminUserPostResponse response = AdminUserPostResponse.builder()
                .postId(10L)
                .boardId(1L)
                .boardName("Free")
                .boardUrl("free")
                .title("hello")
                .deleted(true)
                .secret(true)
                .createdAt(java.time.LocalDateTime.now())
                .build();
        when(userAdminQueryService.getUserPostsForAdmin(1L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/admin/users/{userId}/posts", 1L)
                        .param("page", "0")
                        .param("size", "10")
                        .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].postId").value(10))
                .andExpect(jsonPath("$.data.content[0].deleted").value(true))
                .andExpect(jsonPath("$.data.content[0].secret").value(true));
    }

    @Test
    @DisplayName("get user comments returns admin-specific comment response")
    void getUserComments_returnsAdminSpecificCommentResponse() throws Exception {
        AdminUserCommentResponse response = AdminUserCommentResponse.builder()
                .commentId(20L)
                .content("hidden")
                .deleted(true)
                .depth(1)
                .post(AdminUserCommentResponse.PostInfo.builder()
                        .postId(30L)
                        .title("post")
                        .boardId(40L)
                        .boardName("Free")
                        .boardUrl("free")
                        .deleted(true)
                        .boardActive(false)
                        .boardPublic(false)
                        .build())
                .build();
        when(userAdminQueryService.getUserCommentsForAdmin(1L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/admin/users/{userId}/comments", 1L)
                        .param("page", "0")
                        .param("size", "10")
                        .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].commentId").value(20))
                .andExpect(jsonPath("$.data.content[0].deleted").value(true))
                .andExpect(jsonPath("$.data.content[0].post.deleted").value(true))
                .andExpect(jsonPath("$.data.content[0].post.boardActive").value(false));
    }

    @Test
    @DisplayName("get user subscriptions returns admin-specific subscription response")
    void getUserSubscriptions_returnsAdminSpecificSubscriptionResponse() throws Exception {
        AdminUserSubscriptionResponse response = AdminUserSubscriptionResponse.builder()
                .boardId(50L)
                .boardName("Hidden")
                .boardUrl("hidden")
                .role("MEMBER")
                .boardActive(false)
                .boardPublic(false)
                .subscriptionAccessible(false)
                .inaccessibleReason("INACTIVE")
                .build();
        when(userAdminQueryService.getUserSubscriptionsForAdmin(1L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/admin/users/{userId}/subscriptions", 1L)
                        .param("page", "0")
                        .param("size", "10")
                        .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].boardId").value(50))
                .andExpect(jsonPath("$.data.content[0].boardActive").value(false))
                .andExpect(jsonPath("$.data.content[0].subscriptionAccessible").value(false))
                .andExpect(jsonPath("$.data.content[0].inaccessibleReason").value("INACTIVE"));
    }

    @Test
    @DisplayName("status update returns success")
    void updateUserStatus_returnsSuccess() throws Exception {
        UserStatusUpdateRequest request = new UserStatusUpdateRequest();
        ReflectionTestUtils.setField(request, "status", "ACTIVE");

        mockMvc.perform(put("/api/v1/admin/users/{userId}/status", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userAdminCommandService).updateUserStatus(1L, "ACTIVE");
    }

    @Test
    @DisplayName("status update returns forbidden when activation is blocked")
    void updateUserStatus_returnsForbiddenWhenActivationBlocked() throws Exception {
        UserStatusUpdateRequest request = new UserStatusUpdateRequest();
        ReflectionTestUtils.setField(request, "status", "ACTIVE");
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE))
                .when(userAdminCommandService).updateUserStatus(1L, "ACTIVE");

        mockMvc.perform(put("/api/v1/admin/users/{userId}/status", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.USER_NOT_ACTIVE.getCode()));
    }

    @Test
    @DisplayName("status update returns bad request for deleted users")
    void updateUserStatus_returnsBadRequestWhenDeletedUserIsUpdated() throws Exception {
        UserStatusUpdateRequest request = new UserStatusUpdateRequest();
        ReflectionTestUtils.setField(request, "status", "SUSPENDED");
        doThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE))
                .when(userAdminCommandService).updateUserStatus(1L, "SUSPENDED");

        mockMvc.perform(put("/api/v1/admin/users/{userId}/status", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
    }
}
