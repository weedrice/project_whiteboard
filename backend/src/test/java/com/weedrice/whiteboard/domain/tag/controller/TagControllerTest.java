package com.weedrice.whiteboard.domain.tag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.tag.dto.TagResponse;
import com.weedrice.whiteboard.domain.tag.service.TagService;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = TagController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    })
class TagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TagService tagService;

    @MockBean
    private PostService postService;

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
    @DisplayName("인기 태그 조회 성공")
    void getPopularTags_returnsSuccess() throws Exception {
        // given
        com.weedrice.whiteboard.domain.tag.entity.Tag tag1 = com.weedrice.whiteboard.domain.tag.entity.Tag.builder()
                .tagName("tag1").build();
        com.weedrice.whiteboard.domain.tag.entity.Tag tag2 = com.weedrice.whiteboard.domain.tag.entity.Tag.builder()
                .tagName("tag2").build();
        List<com.weedrice.whiteboard.domain.tag.entity.Tag> tags = List.of(tag1, tag2);
        when(tagService.getPopularTags()).thenReturn(tags);

        // when & then
        mockMvc.perform(get("/api/v1/tags")
                        .with(anonymous())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tags").isArray());
    }

    @Test
    @DisplayName("태그별 게시글 조회 성공 - 인증된 사용자")
    void getPostsByTag_returnsSuccess_whenAuthenticated() throws Exception {
        // given
        Long tagId = 1L;
        PageRequest pageRequest = PageRequest.of(0, 10);
        PostSummary postSummary = PostSummary.builder().build();
        Page<PostSummary> page = new PageImpl<>(List.of(postSummary), pageRequest, 1);

        when(postService.getPostsByTag(eq(tagId), eq(1L), any())).thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/tags/{tagId}/posts", tagId)
                        .param("page", "0")
                        .param("size", "10")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("태그별 게시글 조회 성공 - 비인증 사용자")
    void getPostsByTag_returnsSuccess_whenNotAuthenticated() throws Exception {
        // given
        Long tagId = 1L;
        PageRequest pageRequest = PageRequest.of(0, 10);
        PostSummary postSummary = PostSummary.builder().build();
        Page<PostSummary> page = new PageImpl<>(List.of(postSummary), pageRequest, 1);

        when(postService.getPostsByTag(eq(tagId), isNull(), any())).thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/tags/{tagId}/posts", tagId)
                        .param("page", "0")
                        .param("size", "10")
                        .with(anonymous())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }
}
