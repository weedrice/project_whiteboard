package com.weedrice.whiteboard.domain.board.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardDetailResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardManagerTransferRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        when(boardService.getActiveBoards(any())).thenReturn(List.of(boardListResponse("Admin")));

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
        String boardUrl = "free";
        when(boardService.getBoardDetails(eq(boardUrl), any()))
                .thenReturn(boardDetailResponse("Admin", 1L, false));

        mockMvc.perform(get("/api/v1/boards/{boardUrl}", boardUrl)
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("인기 게시판 목록 조회 성공")
    void getTopBoards_returnsSuccess() throws Exception {
        when(boardService.getTopBoards(any())).thenReturn(List.of(boardListResponse("Admin")));

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
        String boardUrl = "free";
        PostSummary postSummary = PostSummary.builder().build();
        when(postService.getNoticeSummaries(eq(boardUrl), eq(1L))).thenReturn(List.of(postSummary));

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
        BoardCreateRequest request = new BoardCreateRequest("New Board", "newboard", "Description", "icon.png", true);

        when(boardService.createBoard(eq(1L), any())).thenReturn(board);
        when(boardService.getBoardDetails(eq("free"), any()))
                .thenReturn(boardDetailResponse("Admin", 1L, false));

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
        String boardUrl = "free";
        BoardUpdateRequest request = new BoardUpdateRequest();
        ReflectionTestUtils.setField(request, "boardName", "Updated Board");

        when(boardService.updateBoard(eq(boardUrl), any(BoardUpdateRequest.class), any())).thenReturn(board);
        when(boardService.getBoardDetails(eq(boardUrl), any()))
                .thenReturn(boardDetailResponse("Admin", 1L, false));

        mockMvc.perform(put("/api/v1/boards/{boardUrl}", boardUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("게시판 관리자 이양 성공")
    void transferBoardManager_returnsSuccess() throws Exception {
        String boardUrl = "free";
        BoardManagerTransferRequest request = new BoardManagerTransferRequest();
        ReflectionTestUtils.setField(request, "loginId", "nextmanager");

        doNothing().when(boardService).transferBoardManager(eq(boardUrl), eq("nextmanager"), any());
        when(boardService.getBoardDetails(eq(boardUrl), any()))
                .thenReturn(boardDetailResponse("Next Manager", 2L, true));

        mockMvc.perform(put("/api/v1/boards/{boardUrl}/manager", boardUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("구독 순서 변경은 DTO body를 기본으로 사용한다")
    void updateSubscriptionOrder_acceptsDtoBody() throws Exception {
        doNothing().when(boardService).updateSubscriptionOrder(1L, List.of("free", "tech"));

        mockMvc.perform(put("/api/v1/boards/subscriptions/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardUrls\":[\"free\",\"tech\"]}")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("구독 순서 변경은 legacy string 배열 body도 임시 허용한다")
    void updateSubscriptionOrder_acceptsLegacyArrayBody() throws Exception {
        doNothing().when(boardService).updateSubscriptionOrder(1L, List.of("free", "tech"));

        mockMvc.perform(put("/api/v1/boards/subscriptions/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"free\",\"tech\"]")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("구독 순서 변경은 빈 boardUrls를 거부한다")
    void updateSubscriptionOrder_rejectsEmptyBoardUrls() throws Exception {
        mockMvc.perform(put("/api/v1/boards/subscriptions/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardUrls\":[]}")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("구독 순서 변경은 빈 legacy 배열 body를 거부한다")
    void updateSubscriptionOrder_rejectsEmptyLegacyArrayBody() throws Exception {
        mockMvc.perform(put("/api/v1/boards/subscriptions/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("카테고리 생성 시 잘못된 최소 작성 권한은 400")
    void createCategory_invalidMinWriteRole_badRequest() throws Exception {
        mockMvc.perform(post("/api/v1/boards/{boardUrl}/categories", "free")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cat","sortOrder":1,"minWriteRole":"ADMIN"}
                                """)
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("카테고리 수정 시 잘못된 최소 작성 권한은 400")
    void updateCategory_invalidMinWriteRole_badRequest() throws Exception {
        mockMvc.perform(put("/api/v1/boards/categories/{categoryId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cat","sortOrder":1,"minWriteRole":"ADMIN"}
                                """)
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    private BoardListResponse boardListResponse(String adminDisplayName) {
        return new BoardListResponse(board, 0L, 0L, adminDisplayName, false);
    }

    private BoardDetailResponse boardDetailResponse(String adminDisplayName, Long adminUserId, boolean isAdmin) {
        return new BoardDetailResponse(
                board,
                0L,
                0L,
                adminDisplayName,
                adminUserId,
                isAdmin,
                false,
                List.of(),
                List.of(),
                false,
                null);
    }
}
