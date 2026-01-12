package com.weedrice.whiteboard.domain.message.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.message.dto.MessageCreateRequest;
import com.weedrice.whiteboard.domain.message.dto.MessageResponse;
import com.weedrice.whiteboard.domain.message.entity.Message;
import com.weedrice.whiteboard.domain.message.service.MessageService;
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
import org.springframework.test.util.ReflectionTestUtils;
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

@WebMvcTest(controllers = MessageController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    })
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MessageService messageService;

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
    @DisplayName("메시지 전송 성공")
    void sendMessage_returnsSuccess() throws Exception {
        // given
        MessageCreateRequest request = new MessageCreateRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "receiverId", 2L);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "content", "Test message");
        
        Message message = Message.builder().build();
        ReflectionTestUtils.setField(message, "messageId", 1L);
        when(messageService.sendMessage(eq(1L), eq(2L), eq("Test message"))).thenReturn(message);

        // when & then
        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("받은 메시지 조회 성공")
    void getReceivedMessages_returnsSuccess() throws Exception {
        // given
        MessageResponse response = MessageResponse.builder().build();
        when(messageService.getReceivedMessages(eq(1L), any())).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/messages/received")
                        .param("page", "0")
                        .param("size", "20")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("보낸 메시지 조회 성공")
    void getSentMessages_returnsSuccess() throws Exception {
        // given
        MessageResponse response = MessageResponse.builder().build();
        when(messageService.getSentMessages(eq(1L), any())).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/messages/sent")
                        .param("page", "0")
                        .param("size", "20")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("읽지 않은 메시지 개수 조회 성공")
    void getUnreadMessageCount_returnsSuccess() throws Exception {
        // given
        when(messageService.getUnreadMessageCount(eq(1L))).thenReturn(5L);

        // when & then
        mockMvc.perform(get("/api/v1/messages/unread-count")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(5L));
    }
}
