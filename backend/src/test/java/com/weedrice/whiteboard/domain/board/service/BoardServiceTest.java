package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardResponse;
import com.weedrice.whiteboard.domain.board.entity.BoardAiInfo;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscriptionId;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.post.repository.DraftPostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock
    private BoardRepository boardRepository;
    @Mock
    private BoardSubscriptionRepository boardSubscriptionRepository;
    @Mock
    private BoardAiInfoRepository boardAiInfoRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminRepository adminRepository;
    @Mock
    private PostService postService;
    @Mock
    private BoardCategoryRepository boardCategoryRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private DraftPostRepository draftPostRepository;
    @Mock
    private PointService pointService;
    @Mock
    private GlobalConfigService globalConfigService;
    private BoardResponseReadService boardResponseReadService;
    private BoardResponseAssembler boardResponseAssembler;

    private BoardService boardService;
    private BoardAccessPolicy boardAccessPolicy;

    private User user;
    private Board board;

    @BeforeEach
    void setUp() {
        boardResponseReadService = new BoardResponseReadService(
                boardSubscriptionRepository,
                adminRepository,
                boardCategoryRepository,
                boardAiInfoRepository,
                postService);
        boardResponseAssembler = new BoardResponseAssembler(boardResponseReadService);
        boardAccessPolicy = new BoardAccessPolicy(adminRepository);
        boardService = new BoardService(
                boardRepository,
                boardAiInfoRepository,
                boardCategoryRepository,
                boardSubscriptionRepository,
                userRepository,
                adminRepository,
                pointService,
                globalConfigService,
                boardResponseAssembler,
                boardAccessPolicy);

        lenient().when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(anyLong(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(boardCategoryRepository.findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(boardSubscriptionRepository.countByBoardIds(any()))
                .thenReturn(Collections.emptyList());
        lenient().when(adminRepository.findByBoard_BoardIdInAndRoleAndIsActiveOrderByBoard_BoardIdAscAdminIdDesc(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(adminRepository.findByUserAndBoard_BoardIdInAndIsActive(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(boardAiInfoRepository.findByBoard_BoardIdIn(any()))
                .thenReturn(Collections.emptyList());
        lenient().when(boardSubscriptionRepository.findByUserAndBoardIn(any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(postService.getLatestPostsByBoards(any(), anyInt(), any(), any()))
                .thenReturn(Collections.emptyMap());

        user = User.builder()
                .loginId("testuser")
                .password("password")
                .email("test@test.com")
                .displayName("Test User")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        board = Board.builder()
                .boardName("Test Board")
                .boardUrl("test-board")
                .creator(user)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 1L);
        ReflectionTestUtils.setField(board, "isActive", true);
    }

    @Test
    @DisplayName("?쒖꽦?붾맂 寃뚯떆??紐⑸줉 議고쉶 ?깃났")
    void getActiveBoards_success() {
        // given
        when(boardRepository.findByIsActiveOrderBySortOrderAsc(true)).thenReturn(Collections.singletonList(board));

        // when
        List<BoardResponse> activeBoards = boardService.getActiveBoards(null);

        // then
        assertThat(activeBoards).hasSize(1);
        assertThat(activeBoards.get(0).getBoardName()).isEqualTo("Test Board");
    }

    @Test
    @DisplayName("寃뚯떆??援щ룆 ?깃났")
    void subscribeBoard_success() {
        // given
        Long userId = 1L;
        String boardUrl = "test-board";
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl(boardUrl)).thenReturn(Optional.of(board));
        when(boardSubscriptionRepository.findById(any(BoardSubscriptionId.class))).thenReturn(Optional.empty());
        when(boardSubscriptionRepository.findMaxSortOrder(user)).thenReturn(0);
        when(boardSubscriptionRepository.saveAndFlush(any(BoardSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        boardService.subscribeBoard(userId, boardUrl);

        // then
        verify(boardSubscriptionRepository).saveAndFlush(any(BoardSubscription.class));
    }

    @Test
    @DisplayName("寃뚯떆??援щ룆 ?ㅽ뙣 - ?대? 援щ룆??寃쎌슦")
    void subscribeBoard_fail_alreadySubscribed() {
        // given
        Long userId = 1L;
        String boardUrl = "test-board";
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl(boardUrl)).thenReturn(Optional.of(board));
        when(boardSubscriptionRepository.findById(any(BoardSubscriptionId.class)))
                .thenReturn(Optional.of(mock(BoardSubscription.class)));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.subscribeBoard(userId, boardUrl));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_SUBSCRIBED);
    }

    @Test
    @DisplayName("구독 실패 - 저장 시 중복 키면 ALREADY_SUBSCRIBED")
    void subscribeBoard_fail_duplicateKey() {
        Long userId = 1L;
        String boardUrl = "test-board";
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl(boardUrl)).thenReturn(Optional.of(board));
        when(boardSubscriptionRepository.findById(any(BoardSubscriptionId.class))).thenReturn(Optional.empty());
        when(boardSubscriptionRepository.findMaxSortOrder(user)).thenReturn(0);
        when(boardSubscriptionRepository.saveAndFlush(any(BoardSubscription.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.subscribeBoard(userId, boardUrl));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_SUBSCRIBED);
    }

    @Test
    @DisplayName("寃뚯떆???앹꽦 ?깃났")
    void createBoard_success() {
        // given
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(boardRepository.existsByBoardName(request.getBoardName())).thenReturn(false);
        when(boardRepository.existsByBoardUrl(request.getBoardUrl())).thenReturn(false);
        when(globalConfigService.getConfig(anyString())).thenReturn("500");
        when(boardRepository.save(any(Board.class))).thenReturn(board);
        when(boardCategoryRepository.save(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(adminRepository.save(any(com.weedrice.whiteboard.domain.admin.entity.Admin.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);

        // when
        Board createdBoard = boardService.createBoard(creatorId, request);

        // then
        assertThat(createdBoard.getBoardName()).isEqualTo("Test Board");
        InOrder inOrder = inOrder(boardRepository, pointService, boardCategoryRepository, adminRepository);
        inOrder.verify(boardRepository).save(any(Board.class));
        inOrder.verify(pointService).spendPoint(creatorId, 500, "게시판 생성 (Test Board)", 1L, "BOARD_CREATE");
        inOrder.verify(boardCategoryRepository).save(any());
        inOrder.verify(adminRepository).save(any());
    }

    @Test
    @DisplayName("AI ?ъ슜 寃뚯떆???앹꽦 ????λ맂 媛?대뱶 ?꾨줉?꽣? ??ν솕?쒕떎")
    void createBoard_agentEnabled_createsBoardAiInfo() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("AI Board", "ai-board", null, null, true, true, null);

        Board savedBoard = Board.builder()
                .boardName("AI Board")
                .boardUrl("ai-board")
                .description("")
                .creator(user)
                .isPublic(true)
                .agentUseYn(true)
                .build();
        ReflectionTestUtils.setField(savedBoard, "boardId", 2L);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(boardRepository.existsByBoardName(request.getBoardName())).thenReturn(false);
        when(boardRepository.existsByBoardUrl(request.getBoardUrl())).thenReturn(false);
        when(globalConfigService.getConfig(anyString())).thenReturn("500");
        when(boardRepository.save(any(Board.class))).thenReturn(savedBoard);
        when(boardCategoryRepository.save(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(adminRepository.save(any(com.weedrice.whiteboard.domain.admin.entity.Admin.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(boardAiInfoRepository.save(any(BoardAiInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);

        boardService.createBoard(creatorId, request);

        verify(boardAiInfoRepository).save(any(BoardAiInfo.class));
        verify(pointService).spendPoint(creatorId, 500, "게시판 생성 (AI Board)", 2L, "BOARD_CREATE");
    }

    @Test
    @DisplayName("게시판 생성 포인트 부족 예외는 PointService 경로로 유지된다")
    void createBoard_insufficientPoints_propagatesFromPointService() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(boardRepository.existsByBoardName(request.getBoardName())).thenReturn(false);
        when(boardRepository.existsByBoardUrl(request.getBoardUrl())).thenReturn(false);
        when(globalConfigService.getConfig(anyString())).thenReturn("500");
        when(boardRepository.save(any(Board.class))).thenReturn(board);
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        doThrow(new BusinessException(ErrorCode.INSUFFICIENT_POINTS))
                .when(pointService)
                .spendPoint(creatorId, 500, "게시판 생성 (Test Board)", 1L, "BOARD_CREATE");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.createBoard(creatorId, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_POINTS);
        verify(boardRepository).save(any(Board.class));
        verify(boardCategoryRepository, never()).save(any());
        verify(adminRepository, never()).save(any());
        verify(boardAiInfoRepository, never()).save(any());
    }

    @Test
    @DisplayName("?멸린 寃뚯떆??紐⑸줉 議고쉶 ?깃났")
    void getTopBoards_success() {
        // given
        when(boardRepository.findTopBoardsByPostCount(any())).thenReturn(Collections.singletonList(board));

        // when
        List<BoardResponse> boards = boardService.getTopBoards(null);

        // then
        assertThat(boards).hasSize(1);
        verify(boardRepository).findTopBoardsByPostCount(any());
    }

    @Test
    @DisplayName("寃뚯떆???곸꽭 議고쉶 ?깃났")
    void getBoardDetails_success() {
        // given
        String boardUrl = "test-board";
        when(boardRepository.findByBoardUrl(boardUrl)).thenReturn(Optional.of(board));

        // when
        BoardResponse response = boardService.getBoardDetails(boardUrl, null);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getBoardName()).isEqualTo("Test Board");
    }

    @Test
    @DisplayName("寃뚯떆??援щ룆 ?댁젣 ?깃났")
    void unsubscribeBoard_success() {
        // given
        Long userId = 1L;
        String boardUrl = "test-board";
        BoardSubscription subscription = BoardSubscription.builder()
                .user(user)
                .board(board)
                .role("MEMBER")
                .sortOrder(1)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl(boardUrl)).thenReturn(Optional.of(board));
        when(boardSubscriptionRepository.findById(any(BoardSubscriptionId.class))).thenReturn(Optional.of(subscription));

        // when
        boardService.unsubscribeBoard(userId, boardUrl);

        // then
        verify(boardSubscriptionRepository).delete(any());
    }

    @Test
    @DisplayName("내 구독 게시판 조회는 total 과 구독 플래그를 유지한다")
    void getMySubscriptions_preservesTotalAndFlags() {
        BoardSubscription subscription = BoardSubscription.builder()
                .user(user)
                .board(board)
                .role("MEMBER")
                .sortOrder(1)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findAllByUserAndBoard_IsActiveTrueOrderBySortOrderAsc(user))
                .thenReturn(List.of(subscription));
        when(boardSubscriptionRepository.findByUserAndBoardIn(user, List.of(board))).thenReturn(List.of(subscription));

        var result = boardService.getMySubscriptions(1L, PageRequest.of(0, 1));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).isSubscribed()).isTrue();
    }

    @Test
    @DisplayName("구독 순서 변경은 전체 목록과 일치할 때만 1..N으로 재기록한다")
    void updateSubscriptionOrder_rewritesAllSortOrders() {
        Board secondBoard = Board.builder()
                .boardName("Second Board")
                .boardUrl("second-board")
                .creator(user)
                .build();
        ReflectionTestUtils.setField(secondBoard, "boardId", 2L);

        BoardSubscription firstSubscription = BoardSubscription.builder()
                .user(user)
                .board(board)
                .role("MEMBER")
                .sortOrder(1)
                .build();
        BoardSubscription secondSubscription = BoardSubscription.builder()
                .user(user)
                .board(secondBoard)
                .role("MEMBER")
                .sortOrder(2)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findAllByUserAndBoard_IsActiveTrueOrderBySortOrderAsc(user))
                .thenReturn(List.of(firstSubscription, secondSubscription));

        boardService.updateSubscriptionOrder(1L, List.of("second-board", "test-board"));

        assertThat(firstSubscription.getSortOrder()).isEqualTo(2);
        assertThat(secondSubscription.getSortOrder()).isEqualTo(1);
        verify(boardSubscriptionRepository).saveAll(List.of(firstSubscription, secondSubscription));
    }

    @Test
    @DisplayName("구독 순서 변경은 현재 전체 구독 목록과 정확히 일치하지 않으면 거부한다")
    void updateSubscriptionOrder_rejectsMismatch() {
        BoardSubscription subscription = BoardSubscription.builder()
                .user(user)
                .board(board)
                .role("MEMBER")
                .sortOrder(1)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findAllByUserAndBoard_IsActiveTrueOrderBySortOrderAsc(user))
                .thenReturn(List.of(subscription));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.updateSubscriptionOrder(1L, List.of("test-board", "missing-board")));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(boardSubscriptionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("구독 순서 변경은 비활성 보드 구독 이력을 검증 대상에서 제외한다")
    void updateSubscriptionOrder_ignoresInactiveSubscriptions() {
        Board secondBoard = Board.builder()
                .boardName("Second Board")
                .boardUrl("second-board")
                .creator(user)
                .build();
        ReflectionTestUtils.setField(secondBoard, "boardId", 2L);

        BoardSubscription activeSubscription = BoardSubscription.builder()
                .user(user)
                .board(board)
                .role("MEMBER")
                .sortOrder(1)
                .build();
        BoardSubscription anotherActiveSubscription = BoardSubscription.builder()
                .user(user)
                .board(secondBoard)
                .role("MEMBER")
                .sortOrder(2)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findAllByUserAndBoard_IsActiveTrueOrderBySortOrderAsc(user))
                .thenReturn(List.of(activeSubscription, anotherActiveSubscription));

        boardService.updateSubscriptionOrder(1L, List.of("second-board", "test-board"));

        assertThat(activeSubscription.getSortOrder()).isEqualTo(2);
        assertThat(anotherActiveSubscription.getSortOrder()).isEqualTo(1);
        verify(boardSubscriptionRepository).saveAll(List.of(activeSubscription, anotherActiveSubscription));
    }

    @Test
    @DisplayName("문의 게시판 생성 시 탈퇴한 super admin을 creator 후보에서 제외한다")
    void ensureInquiryBoard_usesActiveSuperAdminCreator() {
        UserDetails userDetails = mock(UserDetails.class);
        User activeSuperAdmin = User.builder()
                .loginId("super-admin")
                .password("password")
                .email("super@test.com")
                .displayName("Super Admin")
                .build();
        ReflectionTestUtils.setField(activeSuperAdmin, "userId", 2L);
        activeSuperAdmin.grantSuperAdminRole();
        User deletedSuperAdmin = User.builder()
                .loginId("deleted-admin")
                .password("password")
                .email("deleted@test.com")
                .displayName("Deleted Admin")
                .build();
        ReflectionTestUtils.setField(deletedSuperAdmin, "userId", 1L);
        deletedSuperAdmin.grantSuperAdminRole();
        deletedSuperAdmin.delete();
        List<User> allSuperAdmins = List.of(deletedSuperAdmin, activeSuperAdmin);

        when(userDetails.getUsername()).thenReturn(user.getLoginId());
        when(userRepository.findByLoginId(user.getLoginId())).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("inquiry")).thenReturn(Optional.empty());
        when(userRepository.findByIsSuperAdminTrueAndDeletedAtIsNull()).thenReturn(allSuperAdmins);
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        when(boardRepository.existsByBoardName(anyString())).thenReturn(false);
        when(boardRepository.save(any(Board.class))).thenAnswer(invocation -> {
            Board savedBoard = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedBoard, "boardId", 2L);
            return savedBoard;
        });
        when(adminRepository.findByUserAndBoardAndRole(any(), any(), anyString())).thenReturn(Optional.empty());
        when(adminRepository.save(any(com.weedrice.whiteboard.domain.admin.entity.Admin.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(boardCategoryRepository.save(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boardService.ensureInquiryBoard(userDetails, "custom-inquiry-url");

        ArgumentCaptor<Board> boardCaptor = ArgumentCaptor.forClass(Board.class);
        verify(boardRepository).save(boardCaptor.capture());
        assertThat(boardCaptor.getValue().getCreator()).isEqualTo(activeSuperAdmin);
        verify(userRepository).findByIsSuperAdminTrueAndDeletedAtIsNull();
        verify(userRepository, never()).findByIsSuperAdminTrue();
    }

    @Test
    @DisplayName("구독 순서 변경은 현재 읽을 수 없는 활성 구독을 검증 대상에서 제외한다")
    void updateSubscriptionOrder_ignoresUnreadableSubscriptions() {
        User hiddenBoardCreator = User.builder()
                .loginId("hidden-owner")
                .password("password")
                .email("hidden@test.com")
                .displayName("Hidden Owner")
                .build();
        ReflectionTestUtils.setField(hiddenBoardCreator, "userId", 99L);

        Board hiddenBoard = Board.builder()
                .boardName("Hidden Board")
                .boardUrl("hidden-board")
                .creator(hiddenBoardCreator)
                .isPublic(false)
                .build();
        ReflectionTestUtils.setField(hiddenBoard, "boardId", 3L);

        BoardSubscription visibleSubscription = BoardSubscription.builder()
                .user(user)
                .board(board)
                .role("MEMBER")
                .sortOrder(1)
                .build();
        BoardSubscription hiddenSubscription = BoardSubscription.builder()
                .user(user)
                .board(hiddenBoard)
                .role("MEMBER")
                .sortOrder(2)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findAllByUserAndBoard_IsActiveTrueOrderBySortOrderAsc(user))
                .thenReturn(List.of(visibleSubscription, hiddenSubscription));

        boardService.updateSubscriptionOrder(1L, List.of("test-board"));

        assertThat(visibleSubscription.getSortOrder()).isEqualTo(1);
        assertThat(hiddenSubscription.getSortOrder()).isEqualTo(2);
        verify(boardSubscriptionRepository).saveAll(List.of(visibleSubscription));
    }

    @Test
    @DisplayName("내 구독 목록은 읽을 수 없는 활성 구독을 total 계산에서 제외한다")
    void getMySubscriptions_excludesUnreadableSubscriptionsFromTotal() {
        User hiddenBoardCreator = User.builder()
                .loginId("hidden-owner")
                .password("password")
                .email("hidden@test.com")
                .displayName("Hidden Owner")
                .build();
        ReflectionTestUtils.setField(hiddenBoardCreator, "userId", 99L);

        Board hiddenBoard = Board.builder()
                .boardName("Hidden Board")
                .boardUrl("hidden-board")
                .creator(hiddenBoardCreator)
                .isPublic(false)
                .build();
        ReflectionTestUtils.setField(hiddenBoard, "boardId", 3L);

        BoardSubscription visibleSubscription = BoardSubscription.builder()
                .user(user)
                .board(board)
                .role("MEMBER")
                .sortOrder(1)
                .build();
        BoardSubscription hiddenSubscription = BoardSubscription.builder()
                .user(user)
                .board(hiddenBoard)
                .role("MEMBER")
                .sortOrder(2)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findAllByUserAndBoard_IsActiveTrueOrderBySortOrderAsc(user))
                .thenReturn(List.of(visibleSubscription, hiddenSubscription));
        when(boardSubscriptionRepository.findByUserAndBoardIn(user, List.of(board)))
                .thenReturn(List.of(visibleSubscription));

        var result = boardService.getMySubscriptions(1L, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getBoardUrl()).isEqualTo("test-board");
    }
}
