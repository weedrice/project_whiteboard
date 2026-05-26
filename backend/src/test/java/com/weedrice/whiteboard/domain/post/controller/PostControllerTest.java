package com.weedrice.whiteboard.domain.post.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.post.dto.*;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.Scrap;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.tag.constant.TagConstraints;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebMvcTest(controllers = PostController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class))
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@org.springframework.context.annotation.Import(PostControllerTest.TestSecurityConfig.class)
class PostControllerTest {

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

    @MockitoBean
    private PostService postService;

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
    private User user;
    private Board board;
    private Post post;
    private BoardCategory category;

    @BeforeEach
    void setUp() throws Exception {
        customUserDetails = new CustomUserDetails(1L, "test@example.com", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        user = User.builder().displayName("Test User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(user, "profileImageUrl", "profile.jpg");

        board = Board.builder().boardName("Test Board").build();
        ReflectionTestUtils.setField(board, "boardId", 1L);
        ReflectionTestUtils.setField(board, "boardUrl", "free");
        ReflectionTestUtils.setField(board, "iconUrl", "icon.png");

        category = BoardCategory.builder().name("Cat").board(board).build();
        ReflectionTestUtils.setField(category, "categoryId", 1L);

        post = Post.builder().title("Test Post").contents("Contents").user(user).board(board).category(category)
                .build();
        ReflectionTestUtils.setField(post, "postId", 1L);
        ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.now());

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

    @Nested
    @DisplayName("게시글 조회 API")
    class PostRetrievalApiTests {
        @Test
        @DisplayName("게시글 목록 조회 성공")
        void getPosts_success() throws Exception {
            String boardUrl = "free";
            PostSummary summary = PostSummary.from(post);
            Page<PostSummary> summaryPage = new PageImpl<>(List.of(summary));

            when(postService.getPosts(eq(boardUrl), any(), any(), any(), any(), any(Pageable.class))).thenReturn(summaryPage);

            mockMvc.perform(get("/api/v1/boards/{boardUrl}/posts", boardUrl)
                    .with(user(customUserDetails))
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("게시글 목록 조회 성공 - 비인증 사용자 및 검색어 포함")
        void getPosts_anonymous_withKeyword() throws Exception {
            String boardUrl = "free";
            String keyword = "test";
            PostSummary summary = PostSummary.from(post);
            Page<PostSummary> summaryPage = new PageImpl<>(List.of(summary));

            when(postService.getPosts(eq(boardUrl), any(), eq(keyword), any(), isNull(), any(Pageable.class))).thenReturn(summaryPage);

            mockMvc.perform(get("/api/v1/boards/{boardUrl}/posts", boardUrl)
                    .param("keyword", keyword)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            verify(postService).getPosts(eq(boardUrl), any(), eq(keyword), any(), isNull(), any(Pageable.class));
        }

        @Test
        @DisplayName("게시글 목록 조회 실패 시 비즈니스 예외를 반환한다")
        void getPosts_withKeyword_returnsBusinessExceptionOnFailure() throws Exception {
            String boardUrl = "unknown";
            String keyword = "test";

            doThrow(new com.weedrice.whiteboard.global.exception.BusinessException(
                    com.weedrice.whiteboard.global.exception.ErrorCode.BOARD_NOT_FOUND))
                    .when(postService)
                    .getPosts(eq(boardUrl), any(), eq(keyword), any(), isNull(), any(Pageable.class));

            mockMvc.perform(get("/api/v1/boards/{boardUrl}/posts", boardUrl)
                    .param("keyword", keyword)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("인기 게시글 목록 조회 성공")
        void getTrendingPosts_success() throws Exception {
            PostSummary summary = PostSummary.from(post);
            when(postService.getTrendingPostsPage(any(Pageable.class), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(summary)));

            mockMvc.perform(get("/api/v1/posts/trending")
                    .with(user(customUserDetails))
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].postId").value(post.getPostId()))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("인기 게시글 목록 조회 성공 - 비인증 사용자")
        void getTrendingPosts_anonymous() throws Exception {
            PostSummary summary = PostSummary.from(post);
            when(postService.getTrendingPostsPage(any(Pageable.class), isNull(), any()))
                    .thenReturn(new PageImpl<>(List.of(summary)));

            mockMvc.perform(get("/api/v1/posts/trending"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("단일 게시글 조회 성공")
        void getTrendingPosts_forwardsPeriod() throws Exception {
            PostSummary summary = PostSummary.from(post);
            when(postService.getTrendingPostsPage(any(Pageable.class), eq(1L), eq("30d")))
                    .thenReturn(new PageImpl<>(List.of(summary)));

            mockMvc.perform(get("/api/v1/posts/trending")
                    .param("period", "30d")
                    .with(user(customUserDetails))
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(postService).getTrendingPostsPage(any(Pageable.class), eq(1L), eq("30d"));
        }

        @Test
        @DisplayName("인기 게시글 목록 조회 - 페이지 크기는 최대 100으로 제한")
        void getTrendingPosts_clampsLargePageSize() throws Exception {
            PostSummary summary = PostSummary.from(post);
            when(postService.getTrendingPostsPage(any(Pageable.class), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(summary)));

            mockMvc.perform(get("/api/v1/posts/trending")
                    .param("page", "0")
                    .param("size", "1000")
                    .with(user(customUserDetails))
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(postService).getTrendingPostsPage(captor.capture(), eq(1L), eq("24h"));
            assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("인기 게시글 목록 조회 - 잘못된 페이지 크기는 400")
        void getTrendingPosts_invalidSize_returnsBadRequest() throws Exception {
            clearInvocations(postService);

            mockMvc.perform(get("/api/v1/posts/trending")
                    .param("page", "0")
                    .param("size", "0")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verify(postService, never()).getTrendingPostsPage(any(Pageable.class), any(), any());
        }

        @Test
        @DisplayName("단일 게시글 조회 성공")
        void getPost_success() throws Exception {
            Long postId = 1L;
            PostResponse postResponse = PostResponse.builder().postId(postId).title("Title").build();
            when(postService.getPostResponse(eq(postId), any(), anyBoolean(), eq(20))).thenReturn(postResponse);

            mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                    .with(user(customUserDetails)))
                    .andExpect(status().isOk());

            verify(postService).getPostResponse(postId, 1L, false, 20);
        }

        @Test
        @DisplayName("단일 게시글 조회 - 조회수 증가 명시")
        void getPost_incrementViewTrue_success() throws Exception {
            Long postId = 1L;
            PostResponse postResponse = PostResponse.builder().postId(postId).title("Title").build();
            when(postService.getPostResponse(eq(postId), eq(1L), eq(true), eq(20))).thenReturn(postResponse);

            mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                    .param("incrementView", "true")
                    .with(user(customUserDetails)))
                    .andExpect(status().isOk());

            verify(postService).getPostResponse(postId, 1L, true, 20);
        }

        @Test
        @DisplayName("단일 게시글 조회 성공 - 비인증 사용자")
        void getPost_anonymous() throws Exception {
            Long postId = 1L;
            PostResponse postResponse = PostResponse.builder().build();
            when(postService.getPostResponse(eq(postId), isNull(), anyBoolean(), eq(20))).thenReturn(postResponse);

            mockMvc.perform(get("/api/v1/posts/{postId}", postId))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("단일 게시글 조회 - boardListPageSize 전달")
        void getPost_forwardsBoardListPageSize() throws Exception {
            Long postId = 1L;
            PostResponse postResponse = PostResponse.builder().postId(postId).title("Title").build();
            when(postService.getPostResponse(eq(postId), eq(1L), eq(false), eq(10))).thenReturn(postResponse);

            mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                    .param("incrementView", "false")
                    .param("boardListPageSize", "10")
                    .with(user(customUserDetails)))
                    .andExpect(status().isOk());

            verify(postService).getPostResponse(postId, 1L, false, 10);
        }

        @Test
        @DisplayName("단일 게시글 조회 - boardListPageSize는 최대 100으로 제한")
        void getPost_clampsLargeBoardListPageSize() throws Exception {
            Long postId = 1L;
            PostResponse postResponse = PostResponse.builder().postId(postId).title("Title").build();
            when(postService.getPostResponse(eq(postId), eq(1L), eq(false), eq(100))).thenReturn(postResponse);

            mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                    .param("boardListPageSize", "1000")
                    .with(user(customUserDetails)))
                    .andExpect(status().isOk());

            verify(postService).getPostResponse(postId, 1L, false, 100);
        }

        @Test
        @DisplayName("단일 게시글 조회 - 잘못된 boardListPageSize는 400")
        void getPost_invalidBoardListPageSize_returnsBadRequest() throws Exception {
            Long postId = 1L;
            clearInvocations(postService);

            mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                    .param("boardListPageSize", "0")
                    .with(user(customUserDetails)))
                    .andExpect(status().isBadRequest());

            verify(postService, never()).getPostResponse(anyLong(), any(), anyBoolean(), anyInt());
        }

        @Test
        @DisplayName("조회수 증가 성공")
        void incrementPostView_success() throws Exception {
            Long postId = 1L;
            doNothing().when(postService).incrementViewCount(eq(postId), isNull());

            mockMvc.perform(post("/api/v1/posts/{postId}/view", postId))
                    .andExpect(status().isOk());

            verify(postService).incrementViewCount(postId, null);
        }

        @Test
        @DisplayName("조회수 증가 - 인증 사용자 전달")
        void incrementPostView_authenticatedUser_success() throws Exception {
            Long postId = 1L;
            doNothing().when(postService).incrementViewCount(eq(postId), eq(1L));

            mockMvc.perform(post("/api/v1/posts/{postId}/view", postId)
                    .with(user(customUserDetails)))
                    .andExpect(status().isOk());

            verify(postService).incrementViewCount(postId, 1L);
        }

        @Test
        @DisplayName("조회 기록 업데이트 성공")
        void updateViewHistory_success() throws Exception {
            Long postId = 1L;
            ViewHistoryRequest request = new ViewHistoryRequest(100L, 0L);
            doNothing().when(postService).updateViewHistory(anyLong(), eq(postId), any(ViewHistoryRequest.class));

            mockMvc.perform(put("/api/v1/posts/{postId}/history", postId)
                    .with(user(customUserDetails))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("조회 기록 업데이트 - 음수 체류 시간은 400")
        void updateViewHistory_negativeDuration_validationFailure() throws Exception {
            Long postId = 1L;
            ViewHistoryRequest request = new ViewHistoryRequest(100L, -1L);

            mockMvc.perform(put("/api/v1/posts/{postId}/history", postId)
                    .with(user(customUserDetails))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(postService, never()).updateViewHistory(anyLong(), anyLong(), any(ViewHistoryRequest.class));
        }

        @Test
        @DisplayName("조회 기록 업데이트 - 하루를 초과하는 체류 시간은 400")
        void updateViewHistory_excessiveDuration_validationFailure() throws Exception {
            Long postId = 1L;
            ViewHistoryRequest request = new ViewHistoryRequest(100L, ViewHistoryRequest.MAX_DURATION_MS + 1);

            mockMvc.perform(put("/api/v1/posts/{postId}/history", postId)
                    .with(user(customUserDetails))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("조회 기록 업데이트 - 비인증 사용자는 401")
        void updateViewHistory_unauthorized() throws Exception {
            Long postId = 1L;
            ViewHistoryRequest request = new ViewHistoryRequest(100L, 0L);

            mockMvc.perform(put("/api/v1/posts/{postId}/history", postId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verify(postService, never()).updateViewHistory(anyLong(), anyLong(), any(ViewHistoryRequest.class));
        }
    }

    @Nested
    @DisplayName("게시글 관리 API")
    class PostManagementApiTests {
        @Test
        @DisplayName("게시글 생성 성공")
        void createPost_success() throws Exception {
            String boardUrl = "free";
            PostCreateRequest request = new PostCreateRequest(null, "Title", "Content", List.of("tag"), false, false, false, false, null);
            when(postService.createPost(anyLong(), eq(boardUrl), any())).thenReturn(1L);

            mockMvc.perform(post("/api/v1/boards/{boardUrl}/posts", boardUrl)
                    .with(user(customUserDetails))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("게시글 생성 실패 - 유효성 오류")
        void createPost_fail_validation() throws Exception {
            String boardUrl = "free";
            PostCreateRequest request = new PostCreateRequest(null, "", "", null, false, false, false, false, null);

            mockMvc.perform(post("/api/v1/boards/{boardUrl}/posts", boardUrl)
                    .with(user(customUserDetails))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("게시글 생성 실패 - 공백 태그")
        void createPost_fail_blankTag() throws Exception {
            String boardUrl = "free";
            PostCreateRequest request = new PostCreateRequest(null, "Title", "Content", List.of("   "),
                    false, false, false, false, null);

            mockMvc.perform(post("/api/v1/boards/{boardUrl}/posts", boardUrl)
                    .with(user(customUserDetails))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("게시글 생성 실패 - 태그 개수 초과")
        void createPost_fail_tooManyTags() throws Exception {
            String boardUrl = "free";
            PostCreateRequest request = new PostCreateRequest(null, "Title", "Content",
                    IntStream.rangeClosed(1, TagConstraints.MAX_POST_TAG_COUNT + 1)
                            .mapToObj(index -> "tag" + index)
                            .toList(),
                    false, false, false, false, null);

            mockMvc.perform(post("/api/v1/boards/{boardUrl}/posts", boardUrl)
                    .with(user(customUserDetails))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("게시글 수정 성공")
        void updatePost_success() throws Exception {
            Long postId = 1L;
            PostUpdateRequest request = new PostUpdateRequest(null, "Title", "Content", List.of("tag"), false, false, false, null);
            when(postService.updatePost(anyLong(), eq(postId), any())).thenReturn(postId);

            mockMvc.perform(put("/api/v1/posts/{postId}", postId)
                    .with(user(customUserDetails))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("게시글 수정 실패 - 태그 길이 초과")
        void updatePost_fail_tooLongTag() throws Exception {
            Long postId = 1L;
            PostUpdateRequest request = new PostUpdateRequest(null, "Title", "Content",
                    List.of("a".repeat(TagConstraints.MAX_TAG_NAME_LENGTH + 1)), false, false, false, null);

            mockMvc.perform(put("/api/v1/posts/{postId}", postId)
                    .with(user(customUserDetails))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("게시글 삭제 성공")
        void deletePost_success() throws Exception {
            Long postId = 1L;
            doNothing().when(postService).deletePost(anyLong(), eq(postId));
            mockMvc.perform(delete("/api/v1/posts/{postId}", postId)
                    .with(user(customUserDetails)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("게시글 삭제 실패 - 서비스 예외")
        void deletePost_fail_service() throws Exception {
            Long postId = 1L;
            doThrow(new com.weedrice.whiteboard.global.exception.BusinessException(com.weedrice.whiteboard.global.exception.ErrorCode.FORBIDDEN))
                .when(postService).deletePost(anyLong(), eq(postId));

            mockMvc.perform(delete("/api/v1/posts/{postId}", postId)
                    .with(user(customUserDetails)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("좋아요 및 스크랩 API")
    class LikeAndScrapApiTests {
        @Test
        @DisplayName("좋아요 성공")
        void likePost_success() throws Exception {
            Long postId = 1L;
            when(postService.likePost(anyLong(), eq(postId))).thenReturn(1);
            mockMvc.perform(post("/api/v1/posts/{postId}/like", postId).with(user(customUserDetails)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("좋아요 취소 성공")
        void unlikePost_success() throws Exception {
            Long postId = 1L;
            when(postService.unlikePost(anyLong(), eq(postId))).thenReturn(0);
            mockMvc.perform(delete("/api/v1/posts/{postId}/like", postId).with(user(customUserDetails)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("스크랩 성공 - Request Body 있음")
        void scrapPost_withRequest() throws Exception {
            Long postId = 1L;
            PostScrapRequest request = new PostScrapRequest("Remark");
            doNothing().when(postService).scrapPost(anyLong(), eq(postId), anyString());
            mockMvc.perform(post("/api/v1/posts/{postId}/scrap", postId)
                    .with(user(customUserDetails))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("스크랩 생성 - remark 길이 초과는 400")
        void scrapPost_longRemark_validationFailure() throws Exception {
            Long postId = 1L;
            PostScrapRequest request = new PostScrapRequest("a".repeat(256));

            mockMvc.perform(post("/api/v1/posts/{postId}/scrap", postId)
                    .with(user(customUserDetails))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(postService, never()).scrapPost(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("스크랩 성공 - Request Body 없음")
        void scrapPost_noRequest() throws Exception {
            Long postId = 1L;
            doNothing().when(postService).scrapPost(anyLong(), eq(postId), isNull());
            mockMvc.perform(post("/api/v1/posts/{postId}/scrap", postId).with(user(customUserDetails)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("스크랩 취소 성공")
        void unscrapPost_success() throws Exception {
            Long postId = 1L;
            doNothing().when(postService).unscrapPost(anyLong(), eq(postId));
            mockMvc.perform(delete("/api/v1/posts/{postId}/scrap", postId)
                    .with(user(customUserDetails)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("내 스크랩 조회")
        void getMyScraps_success() throws Exception {
            when(postService.getMyScraps(anyLong(), any())).thenReturn(ScrapListResponse.from(Page.empty()));
            mockMvc.perform(get("/api/v1/users/me/scraps").with(user(customUserDetails)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("임시저장 API")
    class DraftApiTests {
        @Test
        @DisplayName("임시저장 목록 조회")
        void getMyDrafts_success() throws Exception {
            when(postService.getDraftPosts(anyLong(), any())).thenReturn(DraftListResponse.from(Page.empty()));
            mockMvc.perform(get("/api/v1/users/me/drafts").with(user(customUserDetails)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("임시저장 단건 조회")
        void getDraft_success() throws Exception {
            Long draftId = 1L;
            DraftResponse response = DraftResponse.builder()
                    .draftId(draftId)
                    .boardId(1L)
                    .boardUrl("free")
                    .boardName("Test Board")
                    .title("Draft title")
                    .contents("<p>Draft body</p>")
                    .categoryId(1L)
                    .tags(List.of("tag-a", "tag-b"))
                    .isNotice(true)
                    .isNsfw(false)
                    .isSpoiler(true)
                    .isSecret(false)
                    .fileIds(List.of(10L, 11L))
                    .originalPostId(99L)
                    .updatedAt(LocalDateTime.of(2025, 1, 2, 3, 4))
                    .modifiedAt(LocalDateTime.of(2025, 1, 2, 3, 4))
                    .build();
            when(postService.getDraftPost(anyLong(), eq(draftId))).thenReturn(response);
            mockMvc.perform(get("/api/v1/drafts/{draftId}", draftId).with(user(customUserDetails)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.draftId").value(1))
                    .andExpect(jsonPath("$.data.boardId").value(1))
                    .andExpect(jsonPath("$.data.boardUrl").value("free"))
                    .andExpect(jsonPath("$.data.boardName").value("Test Board"))
                    .andExpect(jsonPath("$.data.title").value("Draft title"))
                    .andExpect(jsonPath("$.data.contents").value("<p>Draft body</p>"))
                    .andExpect(jsonPath("$.data.categoryId").value(1))
                    .andExpect(jsonPath("$.data.tags[0]").value("tag-a"))
                    .andExpect(jsonPath("$.data.tags[1]").value("tag-b"))
                    .andExpect(jsonPath("$.data.isNotice").value(true))
                    .andExpect(jsonPath("$.data.isNsfw").value(false))
                    .andExpect(jsonPath("$.data.isSpoiler").value(true))
                    .andExpect(jsonPath("$.data.isSecret").value(false))
                    .andExpect(jsonPath("$.data.fileIds[0]").value(10))
                    .andExpect(jsonPath("$.data.fileIds[1]").value(11))
                    .andExpect(jsonPath("$.data.originalPostId").value(99))
                    .andExpect(jsonPath("$.data.updatedAt").exists())
                    .andExpect(jsonPath("$.data.modifiedAt").exists());
        }

        @Test
        @DisplayName("임시저장 저장")
        void saveDraft_success() throws Exception {
            PostDraftRequest request = PostDraftRequest.builder()
                    .boardUrl("free")
                    .title("Title")
                    .contents("Content")
                    .categoryId(1L)
                    .tags(List.of("tag-a", "tag-b"))
                    .isNotice(true)
                    .isNsfw(false)
                    .isSpoiler(true)
                    .isSecret(false)
                    .fileIds(List.of(10L, 11L))
                    .originalPostId(1L)
                    .updatedAt(LocalDateTime.of(2025, 1, 2, 3, 4))
                    .build();
            DraftResponse response = DraftResponse.builder()
                    .draftId(1L)
                    .boardId(1L)
                    .boardUrl("free")
                    .boardName("Test Board")
                    .title("Title")
                    .contents("Content")
                    .categoryId(1L)
                    .tags(List.of("tag-a", "tag-b"))
                    .isNotice(true)
                    .isNsfw(false)
                    .isSpoiler(true)
                    .isSecret(false)
                    .fileIds(List.of(10L, 11L))
                    .originalPostId(1L)
                    .updatedAt(LocalDateTime.of(2025, 1, 2, 3, 4))
                    .modifiedAt(LocalDateTime.of(2025, 1, 2, 3, 4))
                    .build();
            when(postService.saveDraftPost(anyLong(), any())).thenReturn(response);

            mockMvc.perform(post("/api/v1/drafts").with(user(customUserDetails))
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.draftId").value(1))
                    .andExpect(jsonPath("$.data.boardId").value(1))
                    .andExpect(jsonPath("$.data.boardUrl").value("free"))
                    .andExpect(jsonPath("$.data.boardName").value("Test Board"))
                    .andExpect(jsonPath("$.data.title").value("Title"))
                    .andExpect(jsonPath("$.data.contents").value("Content"))
                    .andExpect(jsonPath("$.data.categoryId").value(1))
                    .andExpect(jsonPath("$.data.tags[0]").value("tag-a"))
                    .andExpect(jsonPath("$.data.tags[1]").value("tag-b"))
                    .andExpect(jsonPath("$.data.isNotice").value(true))
                    .andExpect(jsonPath("$.data.isNsfw").value(false))
                    .andExpect(jsonPath("$.data.isSpoiler").value(true))
                    .andExpect(jsonPath("$.data.isSecret").value(false))
                    .andExpect(jsonPath("$.data.fileIds[0]").value(10))
                    .andExpect(jsonPath("$.data.fileIds[1]").value(11))
                    .andExpect(jsonPath("$.data.originalPostId").value(1))
                    .andExpect(jsonPath("$.data.updatedAt").exists())
                    .andExpect(jsonPath("$.data.modifiedAt").exists());
        }

        @Test
        @DisplayName("임시글 본문이 길이 제한을 넘으면 저장하지 않는다")
        void saveDraft_rejectsTooLongContents() throws Exception {
            PostDraftRequest request = PostDraftRequest.builder()
                    .boardUrl("free")
                    .title("Title")
                    .contents("a".repeat(100001))
                    .build();
            clearInvocations(postService);

            mockMvc.perform(post("/api/v1/drafts").with(user(customUserDetails))
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));

            verify(postService, never()).saveDraftPost(anyLong(), any());
        }

        @Test
        @DisplayName("임시글 저장은 잘못된 boardUrl을 거부한다")
        void saveDraft_rejectsInvalidBoardUrl() throws Exception {
            PostDraftRequest request = PostDraftRequest.builder()
                    .boardUrl("Free Board")
                    .title("Title")
                    .contents("Content")
                    .build();
            clearInvocations(postService);

            mockMvc.perform(post("/api/v1/drafts").with(user(customUserDetails))
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));

            verify(postService, never()).saveDraftPost(anyLong(), any());
        }

        @Test
        @DisplayName("임시저장 삭제")
        void deleteDraft_success() throws Exception {
            doNothing().when(postService).deleteDraftPost(anyLong(), eq(1L));
            mockMvc.perform(delete("/api/v1/drafts/1").with(user(customUserDetails)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("게시글 버전 조회")
    void getPostVersions_success() throws Exception {
        when(postService.getPostVersions(anyLong(), anyLong())).thenReturn(List.of(PostVersionResponse.builder()
                .versionId(10L)
                .actionType("MODIFY")
                .title("Original title")
                .createdAt(LocalDateTime.of(2026, 5, 26, 16, 0))
                .build()));
        mockMvc.perform(get("/api/v1/posts/1/versions").with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].versionId").value(10L))
                .andExpect(jsonPath("$.data[0].actionType").value("MODIFY"))
                .andExpect(jsonPath("$.data[0].title").value("Original title"))
                .andExpect(jsonPath("$.data[0].createdAt").exists());
    }

    @Test
    @DisplayName("게시글 버전 조회 실패 - 비인증 사용자")
    void getPostVersions_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/posts/1/versions"))
                .andExpect(status().isUnauthorized());
    }
}
