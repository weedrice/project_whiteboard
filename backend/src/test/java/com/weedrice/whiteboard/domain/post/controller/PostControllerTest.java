package com.weedrice.whiteboard.domain.post.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.post.dto.PostResponse;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.dto.ViewHistoryRequest;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.search.service.SearchService;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PostService postService;

    @MockBean
    private SearchService searchService;

    private CustomUserDetails customUserDetails;

    @BeforeEach
    void setUp() {
        customUserDetails = new CustomUserDetails(1L, "test@example.com", "password", Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        Authentication authentication = new UsernamePasswordAuthenticationToken(customUserDetails, null, customUserDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    @DisplayName("게시글 목록 조회 성공")
    void getPosts_success() throws Exception {
        // Given
        String boardUrl = "free";
        Pageable pageable = PageRequest.of(0, 10);

        Post mockPost = Post.builder()
                .postId(1L)
                .title("Test Post")
                .content("Content of test post")
                .likeCount(5)
                .viewCount(10)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        List<Post> postList = Collections.singletonList(mockPost);
        Page<Post> postPage = new PageImpl<>(postList, pageable, 1);

        PostSummary mockSummary = PostSummary.from(mockPost);
        mockSummary.setRowNum(1L); // Assuming totalElements = 1, page = 0, size = 10, index = 0

        when(postService.getPosts(eq(boardUrl), any(), any(), any(Pageable.class))).thenReturn(postPage);

        // When & Then
        mockMvc.perform(get("/api/v1/boards/{boardUrl}/posts", boardUrl)
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].postId").value(mockSummary.getPostId()))
                .andExpect(jsonPath("$.data.content[0].title").value(mockSummary.getTitle()))
                .andExpect(jsonPath("$.data.content[0].rowNum").value(mockSummary.getRowNum()));
    }

    @Test
    @DisplayName("게시글 목록 조회 - 키워드 검색 성공")
    void getPosts_withKeyword_success() throws Exception {
        // Given
        String boardUrl = "free";
        String keyword = "search_term";
        Pageable pageable = PageRequest.of(0, 10);

        Post mockPost = Post.builder()
                .postId(2L)
                .title("Keyword Post")
                .content("Content with search_term")
                .likeCount(3)
                .viewCount(7)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        List<Post> postList = Collections.singletonList(mockPost);
        Page<Post> postPage = new PageImpl<>(postList, pageable, 1);

        PostSummary mockSummary = PostSummary.from(mockPost);
        mockSummary.setRowNum(1L);

        when(postService.getPosts(eq(boardUrl), any(), eq(keyword), any(Pageable.class))).thenReturn(postPage);

        // When & Then
        mockMvc.perform(get("/api/v1/boards/{boardUrl}/posts", boardUrl)
                        .param("keyword", keyword)
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].title").value(mockSummary.getTitle()));
    }

    @Test
    @DisplayName("인기 게시글 목록 조회 성공")
    void getTrendingPosts_success() throws Exception {
        // Given
        int limit = 10;
        Post mockPost1 = Post.builder()
                .postId(1L)
                .title("Trending Post 1")
                .content("Content 1")
                .likeCount(10)
                .viewCount(100)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Post mockPost2 = Post.builder()
                .postId(2L)
                .title("Trending Post 2")
                .content("Content 2")
                .likeCount(8)
                .viewCount(80)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        List<PostSummary> trendingPosts = List.of(PostSummary.from(mockPost1), PostSummary.from(mockPost2));

        when(postService.getTrendingPosts(eq(limit), anyLong())).thenReturn(trendingPosts);

        // When & Then
        mockMvc.perform(get("/api/v1/posts/trending")
                        .param("limit", String.valueOf(limit))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.size()").value(2))
                .andExpect(jsonPath("$.data[0].postId").value(mockPost1.getPostId()))
                .andExpect(jsonPath("$.data[1].postId").value(mockPost2.getPostId()));
    }

    @Test
    @DisplayName("단일 게시글 조회 성공")
    void getPost_success() throws Exception {
        // Given
        Long postId = 1L;
        Long userId = customUserDetails.getUserId();

        Post mockPost = Post.builder()
                .postId(postId)
                .title("Single Post Title")
                .content("Single Post Content")
                .likeCount(15)
                .viewCount(200)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        List<String> tags = List.of("tag1", "tag2");
        boolean isLiked = true;
        boolean isScrapped = false;
        ViewHistory viewHistory = ViewHistory.builder()
                .viewHistoryId(1L)
                .postId(postId)
                .userId(userId)
                .lastViewedAt(LocalDateTime.now())
                .build();
        List<String> imageUrls = List.of("http://example.com/image1.jpg");

        when(postService.getPostById(eq(postId), eq(userId))).thenReturn(mockPost);
        when(postService.getTagsForPost(eq(postId))).thenReturn(tags);
        when(postService.isPostLikedByUser(eq(postId), eq(userId))).thenReturn(isLiked);
        when(postService.isPostScrappedByUser(eq(postId), eq(userId))).thenReturn(isScrapped);
        when(postService.getViewHistory(eq(userId), eq(postId))).thenReturn(viewHistory);
        when(postService.getPostImageUrls(eq(postId))).thenReturn(imageUrls);

        // When & Then
        mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.postId").value(postId))
                .andExpect(jsonPath("$.data.title").value("Single Post Title"))
                .andExpect(jsonPath("$.data.content").value("Single Post Content"))
                .andExpect(jsonPath("$.data.tags[0]").value("tag1"))
                .andExpect(jsonPath("$.data.liked").value(isLiked))
                .andExpect(jsonPath("$.data.scrapped").value(isScrapped))
                .andExpect(jsonPath("$.data.viewHistory.viewHistoryId").value(viewHistory.getViewHistoryId()))
                .andExpect(jsonPath("$.data.imageUrls[0]").value("http://example.com/image1.jpg"));
    }

    @Test
    @DisplayName("조회 기록 업데이트 성공")
    void updateViewHistory_success() throws Exception {
        // Given
        Long postId = 1L;
        Long userId = customUserDetails.getUserId();
        ViewHistoryRequest request = new ViewHistoryRequest(100); // Example scroll position

        doNothing().when(postService).updateViewHistory(eq(userId), eq(postId), any(ViewHistoryRequest.class));

        // When & Then
        mockMvc.perform(put("/api/v1/posts/{postId}/history", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist()); // Expecting null data for success
    }

    // TODO: Add more tests for other endpoints in PostController
    // - createPost
    // - updatePost
    // - deletePost
    // - likePost
    // - unlikePost
    // - scrapPost
    // - unscrapPost
    // - getMyScraps
    // - getMyDrafts
    // - getDraft
    // - saveDraft
    // - deleteDraft
    // - getPostVersions
}
