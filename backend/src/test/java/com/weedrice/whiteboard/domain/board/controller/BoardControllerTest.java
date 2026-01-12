package com.weedrice.whiteboard.domain.board.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.service.PostService;
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

@WebMvcTest(controllers = BoardController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    })
class BoardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BoardService boardService;

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
    private Board board;

    @BeforeEach
    void setUp() throws Exception {
        customUserDetails = new CustomUserDetails(1L, "test@example.com", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        
        board = Board.builder().boardName("Test Board").build();
        ReflectionTestUtils.setField(board, "boardUrl", "free");
        
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
    @DisplayName("게시판 목록 조회 성공")
    void getBoards_returnsSuccess() throws Exception {
        // given
        BoardResponse boardResponse = new BoardResponse(board, 0L, "Admin", 1L, false, false, List.of(), List.of());
        when(boardService.getActiveBoards(any())).thenReturn(List.of(boardResponse));

        // when & then
        mockMvc.perform(get("/api/v1/boards")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("게시판 상세 조회 성공")
    void getBoardDetails_returnsSuccess() throws Exception {
        // given
        String boardUrl = "free";
        BoardResponse boardResponse = new BoardResponse(board, 0L, "Admin", 1L, false, false, List.of(), List.of());
        when(boardService.getBoardDetails(eq(boardUrl), any())).thenReturn(boardResponse);

        // when & then
        mockMvc.perform(get("/api/v1/boards/{boardUrl}", boardUrl)
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("인기 게시판 목록 조회 성공")
    void getTopBoards_returnsSuccess() throws Exception {
        // given
        BoardResponse boardResponse = new BoardResponse(board, 0L, "Admin", 1L, false, false, List.of(), List.of());
        when(boardService.getTopBoards(any())).thenReturn(List.of(boardResponse));

        // when & then
        mockMvc.perform(get("/api/v1/boards/top")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("공지사항 목록 조회 성공")
    void getNotices_returnsSuccess() throws Exception {
        // given
        String boardUrl = "free";
        PostSummary postSummary = PostSummary.builder().build();
        when(postService.getNoticeSummaries(eq(boardUrl), eq(1L))).thenReturn(List.of(postSummary));

        // when & then
        mockMvc.perform(get("/api/v1/boards/{boardUrl}/notices", boardUrl)
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("게시판 생성 성공")
    void createBoard_returnsSuccess() throws Exception {
        // given
        BoardCreateRequest request = new BoardCreateRequest("New Board", "newboard", "Description", "icon.png");
        BoardResponse boardResponse = new BoardResponse(board, 0L, "Admin", 1L, false, false, List.of(), List.of());
        
        when(boardService.createBoard(eq(1L), any())).thenReturn(board);
        when(boardService.getBoardDetails(eq("free"), any())).thenReturn(boardResponse);

        // when & then
        mockMvc.perform(post("/api/v1/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("게시판 수정 성공")
    void updateBoard_returnsSuccess() throws Exception {
        // given
        String boardUrl = "free";
        BoardUpdateRequest request = new BoardUpdateRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(request, "boardName", "Updated Board");
        BoardResponse boardResponse = new BoardResponse(board, 0L, "Admin", 1L, false, false, List.of(), List.of());
        
        when(boardService.getBoardDetails(eq(boardUrl), any())).thenReturn(boardResponse);

        // when & then
        mockMvc.perform(put("/api/v1/boards/{boardUrl}", boardUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
