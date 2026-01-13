package com.weedrice.whiteboard.domain.post.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.post.dto.*;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.Scrap;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.search.service.SearchService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

    @MockBean
    private PostService postService;

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

            when(postService.getPosts(eq(boardUrl), any(), any(), any(), any(Pageable.class))).thenReturn(summaryPage);

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

            when(postService.getPosts(eq(boardUrl), any(), any(), isNull(), any(Pageable.class))).thenReturn(summaryPage);
            doNothing().when(searchService).recordSearch(isNull(), eq(keyword));

            mockMvc.perform(get("/api/v1/boards/{boardUrl}/posts", boardUrl)
                    .param("keyword", keyword)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("인기 게시글 목록 조회 성공")
        void getTrendingPosts_success() throws Exception {
            PostSummary summary = PostSummary.from(post);
            when(postService.getTrendingPosts(any(Pageable.class), any())).thenReturn(List.of(summary));

            mockMvc.perform(get("/api/v1/posts/trending")
                    .with(user(customUserDetails))
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("인기 게시글 목록 조회 성공 - 비인증 사용자")
        void getTrendingPosts_anonymous() throws Exception {
            PostSummary summary = PostSummary.from(post);
            when(postService.getTrendingPosts(any(Pageable.class), isNull())).thenReturn(List.of(summary));

            mockMvc.perform(get("/api/v1/posts/trending"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("단일 게시글 조회 성공")
        void getPost_success() throws Exception {
            Long postId = 1L;
            PostResponse postResponse = PostResponse.builder().postId(postId).title("Title").build();
            when(postService.getPostResponse(eq(postId), any(), anyBoolean())).thenReturn(postResponse);

            mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                    .with(user(customUserDetails)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("단일 게시글 조회 성공 - 비인증 사용자")
        void getPost_anonymous() throws Exception {
            Long postId = 1L;
            PostResponse postResponse = PostResponse.builder().build();
            when(postService.getPostResponse(eq(postId), isNull(), anyBoolean())).thenReturn(postResponse);

            mockMvc.perform(get("/api/v1/posts/{postId}", postId))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("조회수 증가 성공")
        void incrementPostView_success() throws Exception {
            Long postId = 1L;
            doNothing().when(postService).incrementViewCount(eq(postId));

            mockMvc.perform(post("/api/v1/posts/{postId}/view", postId))
                    .andExpect(status().isOk());
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
    }

    @Nested
    @DisplayName("게시글 관리 API")
    class PostManagementApiTests {
        @Test
        @DisplayName("게시글 생성 성공")
        void createPost_success() throws Exception {
            String boardUrl = "free";
            PostCreateRequest request = new PostCreateRequest(null, "Title", "Content", List.of("tag"), false, false, false, null);
            when(postService.createPost(anyLong(), eq(boardUrl), any())).thenReturn(post);

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
            PostCreateRequest request = new PostCreateRequest(null, "", "", null, false, false, false, null);

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
            PostUpdateRequest request = new PostUpdateRequest(null, "Title", "Content", List.of("tag"), false, false, null);
            when(postService.updatePost(anyLong(), eq(postId), any())).thenReturn(post);

            mockMvc.perform(put("/api/v1/posts/{postId}", postId)
                    .with(user(customUserDetails))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
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
            DraftResponse response = DraftResponse.builder().build();
            when(postService.getDraftPost(anyLong(), eq(draftId))).thenReturn(response);
            mockMvc.perform(get("/api/v1/drafts/{draftId}", draftId).with(user(customUserDetails)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("임시저장 저장")
        void saveDraft_success() throws Exception {
            PostDraftRequest request = new PostDraftRequest(null, "free", "Title", "Content", null);
            DraftPost draft = DraftPost.builder().title("Title").build();
            ReflectionTestUtils.setField(draft, "draftId", 1L);
            when(postService.saveDraftPost(anyLong(), any())).thenReturn(draft);

            mockMvc.perform(post("/api/v1/drafts").with(user(customUserDetails))
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
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
        when(postService.getPostVersions(anyLong())).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/v1/posts/1/versions").with(user(customUserDetails)))
                .andExpect(status().isOk());
    }
}
