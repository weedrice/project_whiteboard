package com.weedrice.whiteboard.domain.notification.controller;

import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.service.NotificationService;
import com.weedrice.whiteboard.domain.notification.service.CommentTopicAccessService;
import com.weedrice.whiteboard.domain.notification.web.NotificationSseEmitterRegistry;
import com.weedrice.whiteboard.global.config.CurrentUserIdWebMvcConfig;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.exception.GlobalExceptionHandler;
import com.weedrice.whiteboard.global.security.CurrentUserIdArgumentResolver;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    })
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import({
        NotificationControllerTest.TestSecurityConfig.class,
        GlobalExceptionHandler.class,
        CurrentUserIdWebMvcConfig.class,
        CurrentUserIdArgumentResolver.class
})
class NotificationControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    static class TestSecurityConfig {
        @org.springframework.context.annotation.Bean
        public org.springframework.security.web.SecurityFilterChain filterChain(org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private CommentTopicAccessService commentTopicAccessService;

    @MockitoBean
    private NotificationSseEmitterRegistry notificationSseEmitterRegistry;

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

    @MockitoBean
    private org.springframework.context.MessageSource messageSource;

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
    @DisplayName("알림 목록 조회는 페이지 크기를 최대 100으로 제한한다")
    void getNotifications_clampsLargePageSize() throws Exception {
        NotificationResponse response = NotificationResponse.builder().build();
        when(notificationService.getNotifications(eq(1L), any())).thenReturn(response);
        org.mockito.Mockito.clearInvocations(notificationService);

        mockMvc.perform(get("/api/v1/notifications")
                        .param("page", "0")
                        .param("size", "1000")
                        .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).getNotifications(eq(1L), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("알림 목록 조회는 잘못된 페이지 파라미터를 400으로 처리한다")
    void getNotifications_invalidPage_returnsBadRequest() throws Exception {
        org.mockito.Mockito.clearInvocations(notificationService);

        mockMvc.perform(get("/api/v1/notifications")
                        .param("page", "-1")
                        .param("size", "20")
                        .with(user(customUserDetails)))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).getNotifications(anyLong(), any());
    }

    @Test
    @DisplayName("SSE 구독 성공")
    void subscribe_success() throws Exception {
        when(notificationSseEmitterRegistry.subscribe(anyLong())).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/v1/notifications/stream")
                        .with(user(customUserDetails)))
                .andExpect(status().isOk());

        verify(notificationService).validateStreamSubscription(1L);
        verify(notificationSseEmitterRegistry).subscribe(1L);
    }

    @Test
    @DisplayName("SSE subscription rejects missing user")
    void subscribe_missingUser_returnsNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.USER_NOT_FOUND))
                .when(notificationService).validateStreamSubscription(1L);

        mockMvc.perform(get("/api/v1/notifications/stream")
                        .with(user(customUserDetails)))
                .andExpect(status().isNotFound());

        verify(notificationSseEmitterRegistry, never()).subscribe(anyLong());
    }

    @Test
    void commentTopicSubscriptionValidatesPostReadAccess() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/comment-topics/{postId}/subscriptions", 10L)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"subscriberId\":\"comments-panel\"}")
                        .with(user(customUserDetails)))
                .andExpect(status().isOk());

        verify(commentTopicAccessService).validateReadable(1L, 10L);
        verify(notificationSseEmitterRegistry).subscribeCommentTopic(1L, 10L, "comments-panel");
    }

    @Test
    void commentTopicSubscriptionRejectsUnreadablePostBeforeRegistration() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.POST_NOT_FOUND))
                .when(commentTopicAccessService).validateReadable(1L, 10L);

        mockMvc.perform(post("/api/v1/notifications/comment-topics/{postId}/subscriptions", 10L)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"subscriberId\":\"comments-panel\"}")
                        .with(user(customUserDetails)))
                .andExpect(status().isNotFound());

        verify(notificationSseEmitterRegistry, never()).subscribeCommentTopic(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("알림 단건 읽음 처리 성공")
    void readNotification_success() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/{notificationId}/read", 10L)
                        .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).readNotification(1L, 10L);
    }

    @Test
    @DisplayName("모든 알림 읽음 처리 성공")
    void readAllNotifications_success() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/read-all")
                        .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).readAllNotifications(1L);
    }

    @Test
    @DisplayName("읽지 않은 알림 수 조회 성공")
    void getUnreadNotificationCount_success() throws Exception {
        when(notificationService.getUnreadNotificationCount(1L)).thenReturn(3L);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(3L));
    }

    @Test
    @DisplayName("인증 principal이 없으면 401로 처리한다")
    void getNotifications_missingPrincipal_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());

        verify(notificationService, never()).getNotifications(anyLong(), any());
    }
}
