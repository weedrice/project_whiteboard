package com.weedrice.whiteboard.domain.search.controller;

import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.search.dto.IntegratedSearchResponse;
import com.weedrice.whiteboard.domain.search.dto.PopularKeywordDto;
import com.weedrice.whiteboard.domain.search.dto.SearchPersonalizationResponse;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchResultResponse;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchService;
import com.weedrice.whiteboard.domain.search.service.SearchPreviewReadService;
import com.weedrice.whiteboard.domain.search.service.SearchRecordFacade;
import com.weedrice.whiteboard.domain.search.service.SearchService;
import com.weedrice.whiteboard.global.config.CurrentUserIdWebMvcConfig;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CurrentUserIdArgumentResolver;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
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
@org.springframework.context.annotation.Import({
        SearchControllerTest.TestSecurityConfig.class,
        CurrentUserIdWebMvcConfig.class,
        CurrentUserIdArgumentResolver.class
})
class SearchControllerTest {

    private static final int MAX_KEYWORD_LENGTH = 255;

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

    @MockitoBean
    private SearchService searchService;

    @MockitoBean
    private SearchPreviewReadService searchPreviewReadService;

    @MockitoBean
    private SearchRecordFacade searchRecordFacade;

    @MockitoBean
    private SemanticSearchService semanticSearchService;

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

        when(searchPreviewReadService.integratedSearch(eq(query), isNull())).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/search")
                .param("q", query)
                .with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.keyword").value(query))
                .andExpect(jsonPath("$.data.postResults.items").isArray())
                .andExpect(jsonPath("$.data.postResults.totalElements").value(0))
                .andExpect(jsonPath("$.data.postResults.page").value(0))
                .andExpect(jsonPath("$.data.postResults.size").value(0))
                .andExpect(jsonPath("$.data.postResults.hasMore").value(false))
                .andExpect(jsonPath("$.data.commentResults.items").isArray())
                .andExpect(jsonPath("$.data.userResults.items").isArray())
                .andExpect(jsonPath("$.data.boardResults").isArray())
                .andExpect(jsonPath("$.data.posts").doesNotExist())
                .andExpect(jsonPath("$.data.boards").doesNotExist());

        verify(searchPreviewReadService).integratedSearch(eq(query), isNull());
        verify(searchRecordFacade).record(null, query);
    }

    @Test
    @DisplayName("통합 검색은 raw 검색어를 서비스에 위임한다")
    void integratedSearch_delegatesRawKeywordToService() throws Exception {
        String rawQuery = " test ";
        String canonicalQuery = "test";
        org.springframework.data.domain.Page<PostSummary> emptyPostPage = new PageImpl<>(List.of());
        org.springframework.data.domain.Page<com.weedrice.whiteboard.domain.comment.dto.CommentResponse> emptyCommentPage = new PageImpl<>(
                List.of());
        org.springframework.data.domain.Page<com.weedrice.whiteboard.domain.user.dto.UserSummary> emptyUserPage = new PageImpl<>(
                List.of());
        IntegratedSearchResponse response = IntegratedSearchResponse.from(emptyPostPage, emptyCommentPage,
                emptyUserPage, java.util.Collections.emptyList(), canonicalQuery);

        when(searchPreviewReadService.integratedSearch(eq(rawQuery), isNull())).thenReturn(response);

        mockMvc.perform(get("/api/v1/search")
                .param("q", rawQuery)
                .with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(searchPreviewReadService).integratedSearch(eq(rawQuery), isNull());
        verify(searchRecordFacade).record(null, canonicalQuery);
    }

    @Test
    @DisplayName("통합 검색은 긴 raw 검색어를 서비스에 위임한다")
    void integratedSearch_delegatesLongRawKeywordToService() throws Exception {
        String rawQuery = "A".repeat(MAX_KEYWORD_LENGTH + 10);
        String canonicalQuery = "A".repeat(MAX_KEYWORD_LENGTH);
        org.springframework.data.domain.Page<PostSummary> emptyPostPage = new PageImpl<>(List.of());
        org.springframework.data.domain.Page<com.weedrice.whiteboard.domain.comment.dto.CommentResponse> emptyCommentPage = new PageImpl<>(
                List.of());
        org.springframework.data.domain.Page<com.weedrice.whiteboard.domain.user.dto.UserSummary> emptyUserPage = new PageImpl<>(
                List.of());
        IntegratedSearchResponse response = IntegratedSearchResponse.from(emptyPostPage, emptyCommentPage,
                emptyUserPage, java.util.Collections.emptyList(), canonicalQuery);

        when(searchPreviewReadService.integratedSearch(eq(rawQuery), isNull())).thenReturn(response);

        mockMvc.perform(get("/api/v1/search")
                        .param("q", rawQuery)
                        .with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(searchPreviewReadService).integratedSearch(eq(rawQuery), isNull());
        verify(searchRecordFacade).record(null, canonicalQuery);
    }

    @Test
    @DisplayName("통합 검색은 빈 검색어를 거부한다")
    void integratedSearch_rejectsBlankKeyword() throws Exception {
        when(searchPreviewReadService.integratedSearch(eq("   "), isNull()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        mockMvc.perform(get("/api/v1/search")
                .param("q", "   ")
                .with(anonymous()))
                .andExpect(status().isBadRequest());

        verify(searchPreviewReadService).integratedSearch(eq("   "), isNull());
        verify(searchRecordFacade, never()).record(any(), anyString());
    }

    @Test
    @DisplayName("게시글 검색 성공 - 로그인 사용자")
    void searchPosts_authenticated() throws Exception {
        // given
        String query = "test";
        PageRequest pageRequest = PageRequest.of(0, 10);
        PostSummary postSummary = PostSummary.builder().build();
        Page<PostSummary> page = new PageImpl<>(List.of(postSummary), pageRequest, 1);

        when(searchService.searchPosts(eq(query), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(Sort.class), any()))
                .thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/search/posts")
                .param("q", query)
                .param("page", "0")
                .param("size", "10")
                .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());

        verify(searchService).searchPosts(eq(query), any(), any(), any(), any(), any(), any(), eq(0), eq(10), any(Sort.class), eq(1L));
    }

    @Test
    @DisplayName("semantic search delegates request to semantic service")
    void semanticSearch_authenticated() throws Exception {
        String query = "semantic";
        Page<SemanticSearchResultResponse> page = new PageImpl<>(
                List.of(SemanticSearchResultResponse.builder()
                        .contentType("POST")
                        .contentId(1L)
                        .rankSource("VECTOR")
                        .build()),
                PageRequest.of(0, 10),
                1);
        when(semanticSearchService.search(eq(query), eq("POST"), eq("free"), eq(0), eq(10), eq(1L)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/search/semantic")
                        .param("q", query)
                        .param("contentType", "POST")
                        .param("boardUrl", "free")
                        .param("page", "0")
                        .param("size", "10")
                        .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].rankSource").value("VECTOR"));

        verify(semanticSearchService).search(query, "POST", "free", 0, 10, 1L);
    }

    @Test
    @DisplayName("게시글 검색은 원본 페이지 요청을 서비스에 전달한다")
    void searchPosts_passesRawPageRequestToService() throws Exception {
        String query = "test";
        Page<PostSummary> page = new PageImpl<>(List.of(), PageRequest.of(2, 100), 0);
        when(searchService.searchPosts(eq(query), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(Sort.class), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/search/posts")
                        .param("q", query)
                        .param("page", "2")
                        .param("size", "1000")
                        .param("sort", "unknown,asc")
                        .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<Integer> pageCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(searchService).searchPosts(eq(query), any(), any(), any(), any(), any(), any(),
                pageCaptor.capture(), sizeCaptor.capture(), sortCaptor.capture(), eq(1L));
        assertThat(pageCaptor.getValue()).isEqualTo(2);
        assertThat(sizeCaptor.getValue()).isEqualTo(1000);
        assertThat(sortCaptor.getValue()).isEqualTo(Sort.by(Sort.Order.asc("unknown")));
    }

    @Test
    @DisplayName("게시글 검색은 원본 검색어를 서비스에 전달한다")
    void searchPosts_passesRawKeywordToService() throws Exception {
        String rawQuery = " test ";
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<PostSummary> page = new PageImpl<>(List.of(PostSummary.builder().build()), pageRequest, 1);

        when(searchService.searchPosts(eq(rawQuery), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(Sort.class), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/search/posts")
                .param("q", rawQuery)
                .param("page", "0")
                .param("size", "10")
                .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(searchService).searchPosts(eq(rawQuery), any(), any(), any(), any(), any(), any(), eq(0), eq(10), any(Sort.class), eq(1L));
    }

    @Test
    @DisplayName("게시글 검색은 빈 검색어를 거부한다")
    void searchPosts_rejectsBlankKeyword() throws Exception {
        when(searchService.searchPosts(eq("   "), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(Sort.class), any()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        mockMvc.perform(get("/api/v1/search/posts")
                .param("q", "   ")
                .with(user(customUserDetails)))
                .andExpect(status().isBadRequest());

        verify(searchService).searchPosts(eq("   "), any(), any(), any(), any(), any(), any(), eq(0), eq(20), any(Sort.class), eq(1L));
    }

    @Test
    @DisplayName("게시글 검색 실패 시 비즈니스 예외를 반환한다")
    void searchPosts_failureReturnsBusinessException() throws Exception {
        String query = "test";
        when(searchService.searchPosts(eq(query), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(Sort.class), any()))
                .thenThrow(new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        mockMvc.perform(get("/api/v1/search/posts")
                        .param("q", query)
                        .param("boardUrl", "missing-board")
                        .with(user(customUserDetails)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("통합 검색 실패 시 비즈니스 예외를 반환한다")
    void integratedSearch_failureReturnsBusinessException() throws Exception {
        String query = "test";
        when(searchPreviewReadService.integratedSearch(eq(query), isNull()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        mockMvc.perform(get("/api/v1/search")
                .param("q", query)
                .with(anonymous()))
                .andExpect(status().isBadRequest());
        verify(searchRecordFacade, never()).record(any(), anyString());
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
    @DisplayName("인기 검색어 기간은 raw 값으로 서비스에 위임한다")
    void getPopularKeywords_delegatesRawPeriodToService() throws Exception {
        when(searchService.getPopularKeywords(eq(" weekly "), eq(10))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/search/popular")
                .param("period", " weekly ")
                .param("limit", "10")
                .with(anonymous()))
                .andExpect(status().isOk());

        verify(searchService).getPopularKeywords(" weekly ", 10);
    }

    @Test
    @DisplayName("인기 검색어 제한은 raw 값으로 서비스에 위임한다")
    void getPopularKeywords_delegatesRawLimitToService() throws Exception {
        when(searchService.getPopularKeywords(eq("DAILY"), eq(101))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/search/popular")
                .param("period", "DAILY")
                .param("limit", "101")
                .with(anonymous()))
                .andExpect(status().isOk());

        verify(searchService).getPopularKeywords("DAILY", 101);
    }

    @Test
    @DisplayName("인기 검색어 제한이 0 이하면 400을 반환한다")
    void getPopularKeywords_rejectsInvalidLimit() throws Exception {
        when(searchService.getPopularKeywords(eq("DAILY"), eq(0)))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR));

        mockMvc.perform(get("/api/v1/search/popular")
                .param("limit", "0")
                .with(anonymous()))
                .andExpect(status().isBadRequest());

        verify(searchService).getPopularKeywords("DAILY", 0);
    }

    @Test
    @DisplayName("인기 검색어 기간이 유효하지 않으면 400을 반환한다")
    void getPopularKeywords_rejectsInvalidPeriod() throws Exception {
        when(searchService.getPopularKeywords(eq("YEARLY"), eq(10)))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR));

        mockMvc.perform(get("/api/v1/search/popular")
                .param("period", "YEARLY")
                .with(anonymous()))
                .andExpect(status().isBadRequest());

        verify(searchService).getPopularKeywords("YEARLY", 10);
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
    @DisplayName("최근 검색어 조회는 인증 사용자가 필요하다")
    void getRecentSearches_requiresAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/search/recent")
                .with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
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
    @DisplayName("최근 검색어 삭제 - 없거나 소유하지 않은 logId는 NOT_FOUND")
    void deleteRecentSearch_missingOrNotOwned_returnsNotFound() throws Exception {
        Long logId = 99L;
        doThrow(new BusinessException(ErrorCode.NOT_FOUND))
                .when(searchService).deleteRecentSearch(any(), eq(logId));

        mockMvc.perform(delete("/api/v1/search/recent/{logId}", logId)
                .with(user(customUserDetails)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
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
