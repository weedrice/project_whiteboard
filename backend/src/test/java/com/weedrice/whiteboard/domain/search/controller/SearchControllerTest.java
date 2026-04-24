package com.weedrice.whiteboard.domain.search.controller;

import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.search.dto.IntegratedSearchResponse;
import com.weedrice.whiteboard.domain.search.dto.PopularKeywordDto;
import com.weedrice.whiteboard.domain.search.dto.SearchPersonalizationResponse;
import com.weedrice.whiteboard.domain.search.service.SearchRecordEventPublisher;
import com.weedrice.whiteboard.domain.search.service.SearchService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebMvcTest(controllers = SearchController.class, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@org.springframework.context.annotation.Import(SearchControllerTest.TestSecurityConfig.class)
class SearchControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    static class TestSecurityConfig {
        @org.springframework.context.annotation.Bean
        public org.springframework.security.web.SecurityFilterChain filterChain(
                org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    @MockBean
    private SearchRecordEventPublisher searchRecordEventPublisher;

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
    @DisplayName("통합 검색 성공 - 비인증 사용자")
    void integratedSearch_anonymous() throws Exception {
        // given
        String query = "test";
        org.springframework.data.domain.Page<PostSummary> emptyPostPage = new PageImpl<>(List.of());
        org.springframework.data.domain.Page<com.weedrice.whiteboard.domain.comment.dto.CommentResponse> emptyCommentPage = new PageImpl<>(
                List.of());
        org.springframework.data.domain.Page<com.weedrice.whiteboard.domain.user.dto.UserSummary> emptyUserPage = new PageImpl<>(
                List.of());
        IntegratedSearchResponse response = IntegratedSearchResponse.from(emptyPostPage, emptyCommentPage,
                emptyUserPage, java.util.Collections.emptyList(), query);

        when(searchService.integratedSearch(eq(query), isNull())).thenReturn(response);
        doNothing().when(searchRecordEventPublisher).publish(isNull(), eq(query));

        // when & then
        mockMvc.perform(get("/api/v1/search")
                .param("q", query)
                .with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        org.mockito.InOrder inOrder = inOrder(searchService, searchRecordEventPublisher);
        inOrder.verify(searchService).integratedSearch(eq(query), isNull());
        inOrder.verify(searchRecordEventPublisher).publish(isNull(), eq(query));
    }

    @Test
    @DisplayName("통합 검색은 검색어를 정규화해 검색과 기록에 사용한다")
    void integratedSearch_trimsKeywordBeforeSearchAndRecord() throws Exception {
        String rawQuery = " test ";
        String canonicalQuery = "test";
        org.springframework.data.domain.Page<PostSummary> emptyPostPage = new PageImpl<>(List.of());
        org.springframework.data.domain.Page<com.weedrice.whiteboard.domain.comment.dto.CommentResponse> emptyCommentPage = new PageImpl<>(
                List.of());
        org.springframework.data.domain.Page<com.weedrice.whiteboard.domain.user.dto.UserSummary> emptyUserPage = new PageImpl<>(
                List.of());
        IntegratedSearchResponse response = IntegratedSearchResponse.from(emptyPostPage, emptyCommentPage,
                emptyUserPage, java.util.Collections.emptyList(), canonicalQuery);

        when(searchService.integratedSearch(eq(canonicalQuery), isNull())).thenReturn(response);
        doNothing().when(searchRecordEventPublisher).publish(isNull(), eq(canonicalQuery));

        mockMvc.perform(get("/api/v1/search")
                .param("q", rawQuery)
                .with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        org.mockito.InOrder inOrder = inOrder(searchService, searchRecordEventPublisher);
        inOrder.verify(searchService).integratedSearch(eq(canonicalQuery), isNull());
        inOrder.verify(searchRecordEventPublisher).publish(isNull(), eq(canonicalQuery));
    }

    @Test
    @DisplayName("통합 검색은 빈 검색어를 거부한다")
    void integratedSearch_rejectsBlankKeyword() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                .param("q", "   ")
                .with(anonymous()))
                .andExpect(status().isBadRequest());

        verify(searchService, never()).integratedSearch(anyString(), any());
        verify(searchRecordEventPublisher, never()).publish(any(), anyString());
    }

    @Test
    @DisplayName("게시글 검색 성공 - 로그인 사용자")
    void searchPosts_authenticated() throws Exception {
        // given
        String query = "test";
        PageRequest pageRequest = PageRequest.of(0, 10);
        PostSummary postSummary = PostSummary.builder().build();
        Page<PostSummary> page = new PageImpl<>(List.of(postSummary), pageRequest, 1);

        when(searchService.searchPosts(eq(query), any(), any(), any(), any())).thenReturn(page);
        doNothing().when(searchRecordEventPublisher).publish(any(), eq(query));

        // when & then
        mockMvc.perform(get("/api/v1/search/posts")
                .param("q", query)
                .param("page", "0")
                .param("size", "10")
                .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());

        org.mockito.InOrder inOrder = inOrder(searchService, searchRecordEventPublisher);
        inOrder.verify(searchService).searchPosts(eq(query), any(), any(), any(), eq(1L));
        inOrder.verify(searchRecordEventPublisher).publish(eq(1L), eq(query));
    }

    @Test
    @DisplayName("게시글 검색은 검색어를 정규화해 검색과 기록에 사용한다")
    void searchPosts_trimsKeywordBeforeSearchAndRecord() throws Exception {
        String rawQuery = " test ";
        String canonicalQuery = "test";
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<PostSummary> page = new PageImpl<>(List.of(PostSummary.builder().build()), pageRequest, 1);

        when(searchService.searchPosts(eq(canonicalQuery), any(), any(), any(), any())).thenReturn(page);
        doNothing().when(searchRecordEventPublisher).publish(any(), eq(canonicalQuery));

        mockMvc.perform(get("/api/v1/search/posts")
                .param("q", rawQuery)
                .param("page", "0")
                .param("size", "10")
                .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        org.mockito.InOrder inOrder = inOrder(searchService, searchRecordEventPublisher);
        inOrder.verify(searchService).searchPosts(eq(canonicalQuery), any(), any(), any(), eq(1L));
        inOrder.verify(searchRecordEventPublisher).publish(eq(1L), eq(canonicalQuery));
    }

    @Test
    @DisplayName("게시글 검색은 빈 검색어를 거부한다")
    void searchPosts_rejectsBlankKeyword() throws Exception {
        mockMvc.perform(get("/api/v1/search/posts")
                .param("q", "   ")
                .with(user(customUserDetails)))
                .andExpect(status().isBadRequest());

        verify(searchService, never()).searchPosts(anyString(), any(), any(), any(), any());
        verify(searchRecordEventPublisher, never()).publish(any(), anyString());
    }

    @Test
    @DisplayName("게시글 검색 실패 시 검색 기록을 남기지 않는다")
    void searchPosts_failureDoesNotPublishEvent() throws Exception {
        String query = "test";
        when(searchService.searchPosts(eq(query), any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        mockMvc.perform(get("/api/v1/search/posts")
                        .param("q", query)
                        .param("boardUrl", "missing-board")
                        .with(user(customUserDetails)))
                .andExpect(status().isNotFound());

        verify(searchRecordEventPublisher, never()).publish(any(), anyString());
    }

    @Test
    @DisplayName("통합 검색 실패 시 검색 기록을 남기지 않는다")
    void integratedSearch_failureDoesNotPublishEvent() throws Exception {
        String query = "test";
        when(searchService.integratedSearch(eq(query), isNull()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", query)
                        .with(anonymous()))
                .andExpect(status().isBadRequest());

        verify(searchRecordEventPublisher, never()).publish(any(), anyString());
    }

    @Test
    @DisplayName("인기 검색어 조회 성공")
    void getPopularKeywords_returnsSuccess() throws Exception {
        // given
        List<PopularKeywordDto> keywords = List.of(
                new PopularKeywordDto("keyword1", 10L),
                new PopularKeywordDto("keyword2", 5L));
        when(searchService.getPopularKeywords(eq("DAILY"), eq(10))).thenReturn(keywords);

        // when & then
        mockMvc.perform(get("/api/v1/search/popular")
                .param("period", "DAILY")
                .param("limit", "10")
                .with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.keywords").isArray());
    }

    @Test
    @DisplayName("인기 검색어 조회는 기본 파라미터를 사용한다")
    void getPopularKeywords_usesDefaultParams() throws Exception {
        when(searchService.getPopularKeywords(eq("DAILY"), eq(10))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/search/popular")
                .with(anonymous()))
                .andExpect(status().isOk());

        verify(searchService).getPopularKeywords("DAILY", 10);
    }

    @Test
    @DisplayName("인기 검색어 기간은 대소문자와 공백을 정규화한다")
    void getPopularKeywords_normalizesPeriod() throws Exception {
        when(searchService.getPopularKeywords(eq("WEEKLY"), eq(10))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/search/popular")
                .param("period", " weekly ")
                .param("limit", "10")
                .with(anonymous()))
                .andExpect(status().isOk());

        verify(searchService).getPopularKeywords("WEEKLY", 10);
    }

    @Test
    @DisplayName("인기 검색어 제한은 최대 100으로 보정한다")
    void getPopularKeywords_clampsLimit() throws Exception {
        when(searchService.getPopularKeywords(eq("DAILY"), eq(100))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/search/popular")
                .param("period", "DAILY")
                .param("limit", "101")
                .with(anonymous()))
                .andExpect(status().isOk());

        verify(searchService).getPopularKeywords("DAILY", 100);
    }

    @Test
    @DisplayName("인기 검색어 제한이 0 이하면 400을 반환한다")
    void getPopularKeywords_rejectsInvalidLimit() throws Exception {
        mockMvc.perform(get("/api/v1/search/popular")
                .param("limit", "0")
                .with(anonymous()))
                .andExpect(status().isBadRequest());

        verify(searchService, never()).getPopularKeywords(anyString(), anyInt());
    }

    @Test
    @DisplayName("인기 검색어 기간이 유효하지 않으면 400을 반환한다")
    void getPopularKeywords_rejectsInvalidPeriod() throws Exception {
        mockMvc.perform(get("/api/v1/search/popular")
                .param("period", "YEARLY")
                .with(anonymous()))
                .andExpect(status().isBadRequest());

        verify(searchService, never()).getPopularKeywords(anyString(), anyInt());
    }

    @Test
    @DisplayName("최근 검색어 조회 성공")
    void getRecentSearches_returnsSuccess() throws Exception {
        // given
        org.springframework.data.domain.Page<com.weedrice.whiteboard.domain.search.entity.SearchPersonalization> emptyPage = new PageImpl<>(
                List.of());
        SearchPersonalizationResponse response = SearchPersonalizationResponse.from(emptyPage);
        when(searchService.getRecentSearches(any(), any())).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/search/recent")
                .param("page", "0")
                .param("size", "10")
                .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("최근 검색어 삭제 성공")
    void deleteRecentSearch_returnsSuccess() throws Exception {
        // given
        Long logId = 1L;
        doNothing().when(searchService).deleteRecentSearch(any(), eq(logId));

        // when & then
        mockMvc.perform(delete("/api/v1/search/recent/{logId}", logId)
                .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("모든 최근 검색어 삭제 성공")
    void deleteAllRecentSearches_returnsSuccess() throws Exception {
        // given
        doNothing().when(searchService).deleteAllRecentSearches(any());

        // when & then
        mockMvc.perform(delete("/api/v1/search/recent")
                .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
