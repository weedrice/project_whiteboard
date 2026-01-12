package com.weedrice.whiteboard.domain.search.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.search.dto.IntegratedSearchResponse;
import com.weedrice.whiteboard.domain.search.dto.PopularKeywordDto;
import com.weedrice.whiteboard.domain.search.dto.PopularKeywordResponse;
import com.weedrice.whiteboard.domain.search.dto.SearchPersonalizationResponse;
import com.weedrice.whiteboard.domain.search.service.SearchService;
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
import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = SearchController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    })
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SearchService searchService;

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
    @DisplayName("통합 검색 성공")
    void integratedSearch_returnsSuccess() throws Exception {
        // given
        String query = "test";
        org.springframework.data.domain.Page<PostSummary> emptyPostPage = new PageImpl<>(List.of());
        org.springframework.data.domain.Page<com.weedrice.whiteboard.domain.comment.dto.CommentResponse> emptyCommentPage = new PageImpl<>(List.of());
        org.springframework.data.domain.Page<com.weedrice.whiteboard.domain.user.dto.UserSummary> emptyUserPage = new PageImpl<>(List.of());
        IntegratedSearchResponse response = IntegratedSearchResponse.from(emptyPostPage, emptyCommentPage, emptyUserPage, query);
        
        when(searchService.integratedSearch(eq(query), eq(1L))).thenReturn(response);
        doNothing().when(searchService).recordSearch(eq(1L), eq(query));

        // when & then
        mockMvc.perform(get("/api/v1/search")
                        .param("q", query)
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("게시글 검색 성공")
    void searchPosts_returnsSuccess() throws Exception {
        // given
        String query = "test";
        PageRequest pageRequest = PageRequest.of(0, 10);
        PostSummary postSummary = PostSummary.builder().build();
        Page<PostSummary> page = new PageImpl<>(List.of(postSummary), pageRequest, 1);

        when(searchService.searchPosts(eq(query), any(), any(), any(), eq(1L))).thenReturn(page);
        doNothing().when(searchService).recordSearch(eq(1L), eq(query));

        // when & then
        mockMvc.perform(get("/api/v1/search/posts")
                        .param("q", query)
                        .param("page", "0")
                        .param("size", "10")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @DisplayName("인기 검색어 조회 성공")
    void getPopularKeywords_returnsSuccess() throws Exception {
        // given
        List<PopularKeywordDto> keywords = List.of(
                new PopularKeywordDto("keyword1", 10L),
                new PopularKeywordDto("keyword2", 5L)
        );
        when(searchService.getPopularKeywords(eq("DAILY"), eq(10))).thenReturn(keywords);

        // when & then
        mockMvc.perform(get("/api/v1/search/popular")
                        .param("period", "DAILY")
                        .param("limit", "10")
                        .with(anonymous())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.keywords").isArray());
    }

    @Test
    @DisplayName("최근 검색어 조회 성공")
    void getRecentSearches_returnsSuccess() throws Exception {
        // given
        org.springframework.data.domain.Page<com.weedrice.whiteboard.domain.search.entity.SearchPersonalization> emptyPage = new PageImpl<>(List.of());
        SearchPersonalizationResponse response = SearchPersonalizationResponse.from(emptyPage);
        when(searchService.getRecentSearches(eq(1L), any())).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/search/recent")
                        .param("page", "0")
                        .param("size", "10")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("최근 검색어 삭제 성공")
    void deleteRecentSearch_returnsSuccess() throws Exception {
        // given
        Long logId = 1L;
        doNothing().when(searchService).deleteRecentSearch(eq(1L), eq(logId));

        // when & then
        mockMvc.perform(delete("/api/v1/search/recent/{logId}", logId)
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("모든 최근 검색어 삭제 성공")
    void deleteAllRecentSearches_returnsSuccess() throws Exception {
        // given
        doNothing().when(searchService).deleteAllRecentSearches(eq(1L));

        // when & then
        mockMvc.perform(delete("/api/v1/search/recent")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
