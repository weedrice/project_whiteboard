package com.weedrice.whiteboard.domain.board.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardDetailResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardManagerCandidateResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardManagerTransferRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardApplicationService;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @MockitoBean
    private BoardService boardService;

    @MockitoBean
    private BoardApplicationService boardApplicationService;

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
        when(boardService.getActiveBoards(eq(1L))).thenReturn(List.of(boardListResponse("Admin")));

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
        when(boardService.getBoardDetails(eq(boardUrl), eq(1L)))
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
        when(boardService.getTopBoards(eq(1L))).thenReturn(List.of(boardListResponse("Admin")));

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
        when(boardService.getNoticeSummaries(eq(boardUrl), eq(1L))).thenReturn(List.of(postSummary));

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

        when(boardApplicationService.createBoardDetail(eq(1L), any()))
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

        when(boardApplicationService.updateBoardDetail(eq(boardUrl), any(BoardUpdateRequest.class), eq(1L)))
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
    @DisplayName("게시판 수정은 음수 sortOrder를 거부한다")
    void updateBoard_rejectsNegativeSortOrder() throws Exception {
        mockMvc.perform(put("/api/v1/boards/{boardUrl}", "free")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardName\":\"Updated Board\",\"sortOrder\":-1}")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(boardApplicationService, never()).updateBoardDetail(any(), any(), any());
    }

    @Test
    @DisplayName("게시판 생성은 255자를 초과하는 iconUrl을 거부한다")
    void createBoard_rejectsTooLongIconUrl() throws Exception {
        mockMvc.perform(post("/api/v1/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "boardName": "New Board",
                                  "boardUrl": "newboard",
                                  "description": "Description",
                                  "iconUrl": "%s"
                                }
                                """.formatted("a".repeat(256)))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(boardApplicationService, never()).createBoardDetail(any(), any());
    }

    @Test
    @DisplayName("게시판 수정은 255자를 초과하는 iconUrl을 거부한다")
    void updateBoard_rejectsTooLongIconUrl() throws Exception {
        mockMvc.perform(put("/api/v1/boards/{boardUrl}", "free")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "boardName": "Updated Board",
                                  "iconUrl": "%s"
                                }
                                """.formatted("a".repeat(256)))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(boardApplicationService, never()).updateBoardDetail(any(), any(), any());
    }

    @Test
    @DisplayName("게시판 관리자 이양 성공")
    void transferBoardManager_returnsSuccess() throws Exception {
        String boardUrl = "free";
        BoardManagerTransferRequest request = new BoardManagerTransferRequest();
        ReflectionTestUtils.setField(request, "loginId", "nextmanager");

        when(boardApplicationService.transferBoardManagerDetail(eq(boardUrl), eq("nextmanager"), eq(1L)))
                .thenReturn(boardDetailResponse("Next Manager", 2L, false));

        mockMvc.perform(put("/api/v1/boards/{boardUrl}/manager", boardUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("게시판 관리자 후보 조회 성공")
    void getBoardManagerCandidates_returnsSuccess() throws Exception {
        String boardUrl = "free";
        User candidateUser = User.builder()
                .loginId("manager")
                .password("password")
                .email("manager@test.com")
                .displayName("Manager")
                .build();
        ReflectionTestUtils.setField(candidateUser, "userId", 2L);
        BoardManagerCandidateResponse candidate = BoardManagerCandidateResponse.from(candidateUser, true);

        when(boardService.getBoardManagerCandidates(eq(boardUrl), eq(1L), eq("man"), any()))
                .thenReturn(new PageImpl<>(List.of(candidate), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/boards/{boardUrl}/manager-candidates", boardUrl)
                        .queryParam("q", "man")
                        .queryParam("page", "0")
                        .queryParam("size", "10")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].loginId").value("manager"))
                .andExpect(jsonPath("$.data.content[0].displayName").value("Manager"))
                .andExpect(jsonPath("$.data.content[0].currentManager").value(true))
                .andExpect(jsonPath("$.data.content[0].email").doesNotExist());
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
    @DisplayName("카테고리 생성은 음수 sortOrder를 거부한다")
    void createCategory_negativeSortOrder_badRequest() throws Exception {
        mockMvc.perform(post("/api/v1/boards/{boardUrl}/categories", "free")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cat","sortOrder":-1,"minWriteRole":"USER"}
                                """)
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(boardService, never()).createCategory(any(), any(), any());
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

    @Test
    @DisplayName("카테고리 수정은 음수 sortOrder를 거부한다")
    void updateCategory_negativeSortOrder_badRequest() throws Exception {
        mockMvc.perform(put("/api/v1/boards/categories/{categoryId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cat","sortOrder":-1,"minWriteRole":"USER"}
                                """)
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(boardService, never()).updateCategory(any(), any(), any());
    }

    @Test
    @DisplayName("문의 게시판 URL 상세 조회는 404로 차단한다")
    void getBoardDetails_inquiryBoardUrlReturnsNotFound() throws Exception {
        when(boardService.getBoardDetails(eq("inquiry"), eq(1L)))
                .thenThrow(new BusinessException(ErrorCode.BOARD_NOT_FOUND));

        mockMvc.perform(get("/api/v1/boards/{boardUrl}", "inquiry")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(boardService).getBoardDetails(eq("inquiry"), eq(1L));
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
