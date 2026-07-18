package com.weedrice.whiteboard.domain.message.controller;

import tools.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.message.dto.MessageCreateRequest;
import com.weedrice.whiteboard.domain.message.dto.MessageResponse;
import com.weedrice.whiteboard.domain.message.service.MessageService;
import com.weedrice.whiteboard.global.config.CurrentUserIdWebMvcConfig;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CurrentUserIdArgumentResolver;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.stream.LongStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MessageController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
        })
@Import({
        CurrentUserIdWebMvcConfig.class,
        CurrentUserIdArgumentResolver.class
})
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MessageService messageService;

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
    @DisplayName("메시지 전송 성공")
    void sendMessage_returnsSuccess() throws Exception {
        MessageCreateRequest request = new MessageCreateRequest();
        ReflectionTestUtils.setField(request, "receiverId", 2L);
        ReflectionTestUtils.setField(request, "content", "Test message");

        when(messageService.sendMessage(eq(1L), eq(2L), eq("Test message"))).thenReturn(1L);

        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    @DisplayName("받은 메시지 목록 조회 성공")
    void getReceivedMessages_returnsSuccess() throws Exception {
        when(messageService.getReceivedMessages(eq(1L), any())).thenReturn(MessageResponse.builder().build());

        mockMvc.perform(get("/api/v1/messages/received")
                        .param("page", "0")
                        .param("size", "20")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("메시지 목록은 요청 정렬을 서비스로 전달한다")
    void getReceivedMessages_passesRequestedSort() throws Exception {
        when(messageService.getReceivedMessages(eq(1L), any())).thenReturn(MessageResponse.builder().build());

        mockMvc.perform(get("/api/v1/messages/received")
                        .param("sort", "createdAt,asc")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(messageService).getReceivedMessages(eq(1L), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").isAscending()).isTrue();
    }

    @Test
    @DisplayName("보낸 메시지 목록 조회 성공")
    void getSentMessages_returnsSuccess() throws Exception {
        when(messageService.getSentMessages(eq(1L), any())).thenReturn(MessageResponse.builder().build());

        mockMvc.perform(get("/api/v1/messages/sent")
                        .param("page", "0")
                        .param("size", "20")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("메시지 상세 조회 성공")
    void getMessage_returnsSuccess() throws Exception {
        MessageResponse.MessageSummary response = MessageResponse.MessageSummary.builder()
                .messageId(7L)
                .content("detail")
                .isRead(false)
                .build();
        when(messageService.getMessageSummary(1L, 7L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/messages/{messageId}", 7L)
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.messageId").value(7L));
    }

    @Test
    @DisplayName("메시지 읽음 처리 성공")
    void markAsRead_returnsSuccess() throws Exception {
        doNothing().when(messageService).markAsRead(1L, 7L);

        mockMvc.perform(post("/api/v1/messages/{messageId}/read", 7L)
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("메시지 일괄 삭제 성공")
    void deleteMessages_returnsSuccess() throws Exception {
        List<Long> messageIds = List.of(1L, 2L);
        doNothing().when(messageService).deleteMessages(1L, messageIds);

        mockMvc.perform(delete("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageIds))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(messageService).deleteMessages(1L, messageIds);
    }

    @Test
    @DisplayName("메시지 일괄 삭제 null body는 요청 파싱 단계에서 400으로 응답한다")
    void deleteMessages_nullBody_returnsBadRequest() throws Exception {
        mockMvc.perform(delete("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C008"));

        verifyNoInteractions(messageService);
    }

    @Test
    @DisplayName("메시지 일괄 삭제 500개 초과 요청은 validation 오류로 응답한다")
    void deleteMessages_overLimit_returnsBadRequest() throws Exception {
        List<Long> messageIds = LongStream.rangeClosed(1, 501)
                .boxed()
                .toList();

        mockMvc.perform(delete("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(messageIds))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        verifyNoInteractions(messageService);
    }

    @Test
    @DisplayName("읽지 않은 메시지 개수 조회 성공")
    void getUnreadMessageCount_returnsSuccess() throws Exception {
        when(messageService.getUnreadMessageCount(eq(1L))).thenReturn(5L);

        mockMvc.perform(get("/api/v1/messages/unread-count")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(5L));
    }
}
