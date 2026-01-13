package com.weedrice.whiteboard.domain.comment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.comment.dto.CommentCreateRequest;
import com.weedrice.whiteboard.domain.comment.dto.CommentListResponse;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.dto.CommentUpdateRequest;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import static org.mockito.Mockito.doAnswer;

@WebMvcTest(controllers = CommentController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    })
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@org.springframework.context.annotation.Import(CommentControllerTest.TestSecurityConfig.class)
class CommentControllerTest {

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

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CommentService commentService;

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
    @DisplayName("댓글 목록 조회 성공 - 비인증 사용자")
    void getComments_anonymous() throws Exception {
        // given
        Long postId = 1L;
        PageRequest pageRequest = PageRequest.of(0, 10);
        CommentResponse commentResponse = CommentResponse.builder().build();
        Page<CommentResponse> page = new PageImpl<>(List.of(commentResponse), pageRequest, 1);

        when(commentService.getComments(eq(postId), isNull(), any())).thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("댓글 생성 실패 - 유효성 검사 오류")
    void createComment_fail_validation() throws Exception {
        Long postId = 1L;
        CommentCreateRequest request = new CommentCreateRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "content", ""); // Empty content

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("댓글 조회 성공")
    void getComment_returnsSuccess() throws Exception {
        // given
        Long commentId = 1L;
        CommentResponse commentResponse = CommentResponse.builder().build();
        when(commentService.getComment(eq(commentId))).thenReturn(commentResponse);

        // when & then
        mockMvc.perform(get("/api/v1/comments/{commentId}", commentId)
                        .with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("대댓글 목록 조회 성공")
    void getReplies_returnsSuccess() throws Exception {
        // given
        Long commentId = 1L;
        PageRequest pageRequest = PageRequest.of(0, 10);
        org.springframework.data.domain.Page<com.weedrice.whiteboard.domain.comment.entity.Comment> emptyPage = new PageImpl<>(List.of());
        CommentListResponse response = CommentListResponse.from(emptyPage);
        when(commentService.getReplies(eq(commentId), any())).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/comments/{commentId}/replies", commentId)
                        .param("page", "0")
                        .param("size", "10")
                        .with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("댓글 생성 성공")
    void createComment_returnsSuccess() throws Exception {
        // given
        Long postId = 1L;
        CommentCreateRequest request = new CommentCreateRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "content", "Test comment");
        Comment comment = Comment.builder().build();
        org.springframework.test.util.ReflectionTestUtils.setField(comment, "commentId", 1L);

        when(commentService.createComment(any(), eq(postId), isNull(), eq("Test comment"))).thenReturn(comment);

        // when & then
        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("댓글 수정 성공")
    void updateComment_returnsSuccess() throws Exception {
        // given
        Long commentId = 1L;
        CommentUpdateRequest request = new CommentUpdateRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "content", "Updated comment");
        Comment comment = Comment.builder().build();
        org.springframework.test.util.ReflectionTestUtils.setField(comment, "commentId", commentId);

        when(commentService.updateComment(any(), eq(commentId), eq("Updated comment"))).thenReturn(comment);

        // when & then
        mockMvc.perform(put("/api/v1/comments/{commentId}", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("댓글 삭제 성공")
    void deleteComment_returnsSuccess() throws Exception {
        // given
        Long commentId = 1L;
        doNothing().when(commentService).deleteComment(any(), eq(commentId));

        // when & then
        mockMvc.perform(delete("/api/v1/comments/{commentId}", commentId)
                        .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("댓글 좋아요 성공")
    void likeComment_returnsSuccess() throws Exception {
        // given
        Long commentId = 1L;
        doNothing().when(commentService).likeComment(any(), eq(commentId));

        // when & then
        mockMvc.perform(post("/api/v1/comments/{commentId}/like", commentId)
                        .with(user(customUserDetails)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("댓글 좋아요 취소 성공")
    void unlikeComment_returnsSuccess() throws Exception {
        // given
        Long commentId = 1L;
        doNothing().when(commentService).unlikeComment(any(), eq(commentId));

        // when & then
        mockMvc.perform(delete("/api/v1/comments/{commentId}/like", commentId)
                        .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
