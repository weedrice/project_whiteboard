package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.admin.service.AdminEligibleUserService;
import com.weedrice.whiteboard.domain.admin.service.BoardManagerAssignmentService;
import com.weedrice.whiteboard.domain.board.dto.AdminBoardResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardDetailResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryRequest;
import com.weedrice.whiteboard.domain.board.entity.BoardAiInfo;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscriptionId;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.DraftPostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostLatestReadService;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
    private PostLatestReadService postLatestReadService;
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
    @Mock
    private FileService fileService;
    @Mock
    private AdminEligibleUserService adminEligibleUserService;
    @Mock
    private BoardManagerAssignmentService boardManagerAssignmentService;
    @Mock
    private SanctionService sanctionService;
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
                postRepository,
                postLatestReadService);
        boardResponseAssembler = new BoardResponseAssembler(boardResponseReadService);
        boardAccessPolicy = new BoardAccessPolicy(adminRepository);
        BoardQueryService queryService = new BoardQueryService(
                boardRepository,
                boardCategoryRepository,
                boardSubscriptionRepository,
                adminRepository,
                userRepository,
                boardResponseAssembler,
                boardAccessPolicy,
                postService);
        BoardProvisioningService provisioningService = new BoardProvisioningService(
                boardRepository,
                boardAiInfoRepository,
                boardCategoryRepository,
                userRepository,
                pointService,
                globalConfigService,
                fileService,
                adminEligibleUserService,
                boardManagerAssignmentService);
        BoardSubscriptionService subscriptionService = new BoardSubscriptionService(
                boardRepository,
                boardSubscriptionRepository,
                new UserWritableResolver(userRepository, sanctionService),
                boardAccessPolicy);
        BoardCategoryService categoryService = new BoardCategoryService(boardRepository, boardCategoryRepository);
        boardService = new BoardService(
                queryService,
                provisioningService,
                subscriptionService,
                categoryService);
        new SecurityUtils(userRepository, adminRepository).init();

        lenient().when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(anyLong(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(boardCategoryRepository.findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(boardSubscriptionRepository.countByBoardIds(any()))
                .thenReturn(Collections.emptyList());
        lenient().when(postRepository.countActiveByBoardIds(any()))
                .thenReturn(Collections.emptyList());
        lenient().when(adminRepository.findByBoard_BoardIdInAndRoleAndIsActiveOrderByBoard_BoardIdAscAdminIdDesc(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(adminRepository.findByUserAndBoard_BoardIdInAndIsActive(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(adminRepository.findByBoardAndRoleAndIsActive(any(), anyString(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(adminRepository.findByUserAndBoardAndRole(any(), any(), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(adminRepository.findByUserAndBoardAndIsActive(any(), any(), anyBoolean()))
                .thenReturn(Optional.empty());
        lenient().when(boardAiInfoRepository.findByBoard_BoardIdIn(any()))
                .thenReturn(Collections.emptyList());
        lenient().when(boardSubscriptionRepository.findByUserAndBoardIn(any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(postLatestReadService.getLatestPostsByBoards(any(), anyInt(), any(), any()))
                .thenReturn(Collections.emptyMap());

        user = User.builder()
                .loginId("testuser")
                .password("password")
                .email("test@test.com")
                .displayName("Test User")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        lenient().when(userRepository.findByLoginId(anyString())).thenReturn(Optional.of(user));

        board = Board.builder()
                .boardName("Test Board")
                .boardUrl("test-board")
                .creator(user)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 1L);
        ReflectionTestUtils.setField(board, "isActive", true);
    }

    @Test
    @DisplayName("활성화된 게시판 목록 조회 성공")
    void getActiveBoards_success() {
        // given
        when(boardRepository.findReadableActiveBoardsOrderBySortOrderAscBoardIdAsc(null, false))
                .thenReturn(Collections.singletonList(board));

        // when
        List<BoardListResponse> activeBoards = boardService.getActiveBoards(null);

        // then
        assertThat(activeBoards).hasSize(1);
        assertThat(activeBoards.get(0).getBoardName()).isEqualTo("Test Board");
    }

    @Test
    @DisplayName("super admin은 전체 게시판 목록을 조회할 수 있다")
    void getAllBoards_superAdmin_success() {
        user.grantSuperAdminRole();
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn(user.getLoginId());
        when(boardRepository.findAllByOrderBySortOrderAscBoardIdAsc()).thenReturn(List.of(board));

        List<AdminBoardResponse> responses = boardService.getAllBoards(userDetails);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getBoardId()).isEqualTo(1L);
        verify(boardRepository).findAllByOrderBySortOrderAscBoardIdAsc();
    }

    @Test
    @DisplayName("일반 사용자는 전체 게시판 목록을 조회할 수 없다")
    void getAllBoards_normalUser_forbidden() {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn(user.getLoginId());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.getAllBoards(userDetails));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(boardRepository, never()).findAllByOrderBySortOrderAscBoardIdAsc();
    }

    @Test
    @DisplayName("비인증 요청은 전체 게시판 목록을 조회할 수 없다")
    void getAllBoards_nullPrincipal_forbidden() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.getAllBoards(null));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(boardRepository, never()).findAllByOrderBySortOrderAscBoardIdAsc();
    }

    @Test
    @DisplayName("비활성 super admin은 전체 게시판 목록을 조회할 수 없다")
    void getAllBoards_inactiveSuperAdmin_forbidden() {
        user.grantSuperAdminRole();
        user.suspend();
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn(user.getLoginId());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.getAllBoards(userDetails));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(boardRepository, never()).findAllByOrderBySortOrderAscBoardIdAsc();
    }

    @Test
    @DisplayName("없는 사용자의 전체 게시판 목록 조회는 USER_NOT_FOUND를 반환한다")
    void getAllBoards_missingUser_userNotFound() {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn("missing");
        when(userRepository.findByLoginId("missing")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.getAllBoards(userDetails));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        verify(boardRepository, never()).findAllByOrderBySortOrderAscBoardIdAsc();
    }

    @Test
    @DisplayName("게시판 구독 성공")
    void subscribeBoard_success() {
        // given
        Long userId = 1L;
        String boardUrl = "test-board";
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl(boardUrl)).thenReturn(Optional.of(board));
        when(boardSubscriptionRepository.findById(any(BoardSubscriptionId.class))).thenReturn(Optional.empty());
        when(boardSubscriptionRepository.findMaxSortOrder(user)).thenReturn(0);
        when(boardSubscriptionRepository.saveAndFlush(any(BoardSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        boardService.subscribeBoard(userId, boardUrl);

        // then
        verify(userRepository).findByIdForUpdate(userId);
        verify(boardSubscriptionRepository).saveAndFlush(any(BoardSubscription.class));
    }

    @Test
    @DisplayName("쓰기 불가 사용자는 게시판을 구독할 수 없다")
    void subscribeBoard_rejectsNotWritableUser() {
        // given
        Long userId = 1L;
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE))
                .when(sanctionService).validateNotBanned(user);

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.subscribeBoard(userId, "test-board"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_ACTIVE);
        verify(boardRepository, never()).findByBoardUrl(anyString());
        verify(boardSubscriptionRepository, never()).saveAndFlush(any(BoardSubscription.class));
    }

    @Test
    @DisplayName("이미 구독한 게시판은 다시 구독할 수 없다")
    void subscribeBoard_fail_alreadySubscribed() {
        // given
        Long userId = 1L;
        String boardUrl = "test-board";
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
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
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl(boardUrl)).thenReturn(Optional.of(board));
        when(boardSubscriptionRepository.findById(any(BoardSubscriptionId.class))).thenReturn(Optional.empty());
        when(boardSubscriptionRepository.findMaxSortOrder(user)).thenReturn(0);
        when(boardSubscriptionRepository.saveAndFlush(any(BoardSubscription.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.subscribeBoard(userId, boardUrl));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_SUBSCRIBED);
        verify(boardSubscriptionRepository, never()).existsByUserAndBoard(any(), any());
    }

    @Test
    @DisplayName("구독 실패 - sort_order 유니크 충돌이면 DUPLICATE_RESOURCE")
    void subscribeBoard_fail_duplicateSortOrderConstraint() {
        Long userId = 1L;
        String boardUrl = "test-board";
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl(boardUrl)).thenReturn(Optional.of(board));
        when(boardSubscriptionRepository.findById(any(BoardSubscriptionId.class))).thenReturn(Optional.empty());
        when(boardSubscriptionRepository.findMaxSortOrder(user)).thenReturn(0);
        when(boardSubscriptionRepository.saveAndFlush(any(BoardSubscription.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate",
                        new ConstraintViolationException(
                                "uk_board_subscriptions_user_sort_order",
                                null,
                                "uk_board_subscriptions_user_sort_order")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.subscribeBoard(userId, boardUrl));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
        verify(boardSubscriptionRepository, never()).existsByUserAndBoard(any(), any());
    }

    @Test
    @DisplayName("게시판 관리자 이관은 잠금 조회한 게시판으로 권한 검증 후 위임한다")
    void transferBoardManager_usesLockedBoardLookup() {
        User nextManager = User.builder()
                .loginId("nextmanager")
                .password("password")
                .email("next@test.com")
                .displayName("Next Manager")
                .build();
        ReflectionTestUtils.setField(nextManager, "userId", 2L);

        when(boardRepository.findByBoardUrlForUpdate("test-board")).thenReturn(Optional.of(board));
        when(adminRepository.findByUserAndBoardAndIsActive(user, board, true)).thenReturn(Optional.empty());
        when(adminEligibleUserService.getActiveUserByLoginId("nextmanager")).thenReturn(nextManager);

        authenticateUser();
        try {
            boardService.transferBoardManager("test-board", "nextmanager", null);
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(boardRepository).findByBoardUrlForUpdate("test-board");
        verify(boardRepository, never()).findByBoardUrl("test-board");
        verify(boardManagerAssignmentService).assignBoardManager(board, nextManager);
    }

    @Test
    @DisplayName("게시판 관리자 이관은 권한이 없으면 대상 사용자 조회와 배정을 하지 않는다")
    void transferBoardManager_forbiddenSkipsManagerAssignment() {
        User otherUser = User.builder()
                .loginId("other")
                .password("password")
                .email("other@test.com")
                .displayName("Other")
                .build();
        ReflectionTestUtils.setField(otherUser, "userId", 99L);

        when(boardRepository.findByBoardUrlForUpdate("test-board")).thenReturn(Optional.of(board));
        when(userRepository.findById(99L)).thenReturn(Optional.of(otherUser));
        when(adminRepository.findByUserAndBoardAndIsActive(otherUser, board, true)).thenReturn(Optional.empty());
        CustomUserDetails principal = new CustomUserDetails(99L, otherUser.getLoginId(), "password", Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        BusinessException exception;
        try {
            exception = assertThrows(BusinessException.class,
                    () -> boardService.transferBoardManager("test-board", "nextmanager", null));
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(boardRepository).findByBoardUrlForUpdate("test-board");
        verify(boardRepository, never()).findByBoardUrl("test-board");
        verify(adminEligibleUserService, never()).getActiveUserByLoginId(anyString());
        verify(boardManagerAssignmentService, never()).assignBoardManager(any(), any());
    }

    @Test
    @DisplayName("게시판 관리자 이관은 잠금 조회에서 게시판이 없으면 BOARD_NOT_FOUND를 던진다")
    void transferBoardManager_lockedBoardNotFound() {
        when(boardRepository.findByBoardUrlForUpdate("missing-board")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.transferBoardManager("missing-board", "nextmanager", null));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BOARD_NOT_FOUND);
        verify(boardRepository).findByBoardUrlForUpdate("missing-board");
        verify(boardRepository, never()).findByBoardUrl("missing-board");
        verify(adminEligibleUserService, never()).getActiveUserByLoginId(anyString());
        verify(boardManagerAssignmentService, never()).assignBoardManager(any(), any());
    }

    @Test
    @DisplayName("게시판 생성 성공")
    void createBoard_success() {
        // given
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(boardRepository.existsByBoardName(request.getBoardName())).thenReturn(false);
        when(boardRepository.existsByBoardUrl(request.getBoardUrl())).thenReturn(false);
        when(globalConfigService.getConfig(anyString())).thenReturn("500");
        when(boardRepository.saveAndFlush(any(Board.class))).thenReturn(board);
        when(boardCategoryRepository.save(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);

        // when
        Board createdBoard = boardService.createBoard(creatorId, request);

        // then
        assertThat(createdBoard.getBoardName()).isEqualTo("Test Board");
        InOrder inOrder = inOrder(boardRepository, pointService, boardCategoryRepository, boardManagerAssignmentService);
        inOrder.verify(boardRepository).saveAndFlush(any(Board.class));
        inOrder.verify(pointService).spendPoint(
                eq(creatorId),
                eq(500),
                eq("게시판 생성 (Test Board)"),
                eq(1L),
                eq("BOARD_CREATE"));
        ArgumentCaptor<BoardCategory> categoryCaptor = ArgumentCaptor.forClass(BoardCategory.class);
        inOrder.verify(boardCategoryRepository).save(categoryCaptor.capture());
        inOrder.verify(boardManagerAssignmentService).assignBoardManager(board, user);
        assertThat(categoryCaptor.getValue().getName()).isEqualTo("일반");
    }

    @Test
    @DisplayName("게시판 생성 비용 설정이 잘못되면 기본값으로 차감한다")
    void createBoard_invalidCostConfig_usesDefaultCost() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(boardRepository.existsByBoardName(request.getBoardName())).thenReturn(false);
        when(boardRepository.existsByBoardUrl(request.getBoardUrl())).thenReturn(false);
        when(globalConfigService.getConfig("POINT_BOARD_CREATE_COST")).thenReturn("invalid");
        when(boardRepository.saveAndFlush(any(Board.class))).thenReturn(board);
        when(boardCategoryRepository.save(any(BoardCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);

        boardService.createBoard(creatorId, request);

        verify(pointService).spendPoint(
                eq(creatorId),
                eq(500),
                eq("게시판 생성 (Test Board)"),
                eq(1L),
                eq("BOARD_CREATE"));
    }

    @Test
    @DisplayName("게시판 생성 비용 설정이 0이면 포인트를 차감하지 않는다")
    void createBoard_zeroCostConfig_skipsPointSpend() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(boardRepository.existsByBoardName(request.getBoardName())).thenReturn(false);
        when(boardRepository.existsByBoardUrl(request.getBoardUrl())).thenReturn(false);
        when(globalConfigService.getConfig("POINT_BOARD_CREATE_COST")).thenReturn("0");
        when(boardRepository.saveAndFlush(any(Board.class))).thenReturn(board);
        when(boardCategoryRepository.save(any(BoardCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);

        boardService.createBoard(creatorId, request);

        verify(pointService, never()).spendPoint(anyLong(), anyInt(), anyString(), anyLong(), anyString());
        verify(boardCategoryRepository).save(any(BoardCategory.class));
        verify(boardManagerAssignmentService).assignBoardManager(board, user);
    }

    @Test
    @DisplayName("게시판 생성 시 파일 기반 아이콘이면 영구 연관한다")
    void createBoard_withUploadedIcon_associatesBoardIcon() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest(
                "New Board",
                "new-board",
                "New Description",
                "/api/v1/files/55",
                null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(boardRepository.existsByBoardName(request.getBoardName())).thenReturn(false);
        when(boardRepository.existsByBoardUrl(request.getBoardUrl())).thenReturn(false);
        when(globalConfigService.getConfig(anyString())).thenReturn("500");
        when(boardRepository.saveAndFlush(any(Board.class))).thenAnswer(invocation -> {
            Board savedBoard = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedBoard, "boardId", 1L);
            return savedBoard;
        });
        when(boardCategoryRepository.save(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);

        boardService.createBoard(creatorId, request);

        verify(fileService).replaceBoardIcon(55L, creatorId, 1L);
    }

    @Test
    @DisplayName("게시판 생성 전 작성자의 활성 상태를 검증한다")
    void createBoard_requiresActiveCreator() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE))
                .when(adminEligibleUserService)
                .validateActiveUser(user);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.createBoard(creatorId, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_ACTIVE);
        verify(boardRepository, never()).saveAndFlush(any(Board.class));
        verify(boardManagerAssignmentService, never()).assignBoardManager(any(), any());
    }

    @Test
    @DisplayName("AI 사용 게시판 생성 시 BoardAiInfo를 생성한다")
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
        when(boardRepository.saveAndFlush(any(Board.class))).thenReturn(savedBoard);
        when(boardCategoryRepository.save(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(boardAiInfoRepository.save(any(BoardAiInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);

        boardService.createBoard(creatorId, request);

        verify(boardAiInfoRepository).save(any(BoardAiInfo.class));
        verify(pointService).spendPoint(eq(creatorId), eq(500), anyString(), eq(2L), eq("BOARD_CREATE"));
    }

    @Test
    @DisplayName("Private board creation disables agent use")
    void createBoard_privateBoardDisablesAgentUse() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("Private AI Board", "private-ai", null, null, false, true,
                null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(boardRepository.existsByBoardName(request.getBoardName())).thenReturn(false);
        when(boardRepository.existsByBoardUrl(request.getBoardUrl())).thenReturn(false);
        when(globalConfigService.getConfig(anyString())).thenReturn("500");
        when(boardRepository.saveAndFlush(any(Board.class))).thenAnswer(invocation -> {
            Board savedBoard = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedBoard, "boardId", 3L);
            return savedBoard;
        });
        when(boardCategoryRepository.save(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);

        Board createdBoard = boardService.createBoard(creatorId, request);

        assertThat(createdBoard.getIsPublic()).isFalse();
        assertThat(createdBoard.isAgentEnabled()).isFalse();
        verify(boardAiInfoRepository, never()).save(any(BoardAiInfo.class));
    }

    @Test
    @DisplayName("Private board update disables agent use")
    void updateBoard_privateBoardDisablesAgentUse() {
        UserDetails userDetails = mock(UserDetails.class);
        BoardUpdateRequest request = new BoardUpdateRequest();
        ReflectionTestUtils.setField(request, "boardName", "Private Board");
        ReflectionTestUtils.setField(request, "description", "Updated Description");
        ReflectionTestUtils.setField(request, "boardUrl", "test-board");
        ReflectionTestUtils.setField(request, "iconUrl", null);
        ReflectionTestUtils.setField(request, "sortOrder", 1);
        ReflectionTestUtils.setField(request, "isActive", true);
        ReflectionTestUtils.setField(request, "isPublic", false);
        ReflectionTestUtils.setField(request, "agentUseYn", true);
        ReflectionTestUtils.setField(board, "agentUseYn", true);

        when(userDetails.getUsername()).thenReturn(user.getLoginId());
        when(userRepository.findByLoginId(user.getLoginId())).thenReturn(Optional.of(user));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("test-board")).thenReturn(Optional.of(board));

        CustomUserDetails principal = new CustomUserDetails(1L, user.getLoginId(), "password", Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        Board updatedBoard;
        try {
            updatedBoard = boardService.updateBoard("test-board", request, userDetails);
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(updatedBoard.getIsPublic()).isFalse();
        assertThat(updatedBoard.isAgentEnabled()).isFalse();
        verify(boardAiInfoRepository, never()).save(any(BoardAiInfo.class));
    }

    @Test
    @DisplayName("게시판 수정 시 sortOrder를 생략하면 기존 정렬값을 유지한다")
    void updateBoard_omittedSortOrderKeepsExistingValue() {
        UserDetails userDetails = mock(UserDetails.class);
        BoardUpdateRequest request = new BoardUpdateRequest();
        ReflectionTestUtils.setField(request, "boardName", "Updated Board");
        ReflectionTestUtils.setField(request, "description", "Updated Description");
        ReflectionTestUtils.setField(request, "boardUrl", "test-board");
        ReflectionTestUtils.setField(request, "iconUrl", null);
        ReflectionTestUtils.setField(request, "isActive", true);
        ReflectionTestUtils.setField(request, "isPublic", true);
        ReflectionTestUtils.setField(board, "sortOrder", 7);

        when(userDetails.getUsername()).thenReturn(user.getLoginId());
        when(boardRepository.findByBoardUrl("test-board")).thenReturn(Optional.of(board));

        authenticateUser();
        Board updatedBoard;
        try {
            updatedBoard = boardService.updateBoard("test-board", request, userDetails);
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(updatedBoard.getSortOrder()).isEqualTo(7);
    }

    @Test
    @DisplayName("Board update applies uploaded icon replacement")
    void updateBoard_replacesBoardIcon() {
        UserDetails userDetails = mock(UserDetails.class);
        BoardUpdateRequest request = new BoardUpdateRequest();
        ReflectionTestUtils.setField(request, "boardName", "Updated Board");
        ReflectionTestUtils.setField(request, "description", "Updated Description");
        ReflectionTestUtils.setField(request, "boardUrl", "test-board");
        ReflectionTestUtils.setField(request, "iconUrl", "/api/v1/files/88");
        ReflectionTestUtils.setField(request, "sortOrder", 1);
        ReflectionTestUtils.setField(request, "isActive", true);
        ReflectionTestUtils.setField(request, "isPublic", true);

        ReflectionTestUtils.setField(board, "iconUrl", "/api/v1/files/77");

        when(userDetails.getUsername()).thenReturn(user.getLoginId());
        when(userRepository.findByLoginId(user.getLoginId())).thenReturn(Optional.of(user));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRepository.findByUserAndBoardAndIsActive(user, board, true)).thenReturn(Optional.empty());
        when(boardRepository.findByBoardUrl("test-board")).thenReturn(Optional.of(board));

        CustomUserDetails principal = new CustomUserDetails(1L, user.getLoginId(), "password", Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        Board updatedBoard;
        try {
            updatedBoard = boardService.updateBoard("test-board", request, userDetails);
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(updatedBoard.getIconUrl()).isEqualTo("/api/v1/files/88");
        verify(fileService).replaceBoardIcon(88L, 1L, 1L);
        verify(fileService).deleteFileWithStorageIfAssociated(77L, 1L, FileService.RELATED_TYPE_BOARD_ICON);
    }

    @Test
    @DisplayName("게시판 수정 실패 - 저장 시 board_name 충돌이면 DUPLICATE_BOARD_NAME")
    void updateBoard_duplicateBoardNameDuringFlush() {
        UserDetails userDetails = mock(UserDetails.class);
        BoardUpdateRequest request = createBoardUpdateRequest("Updated Board", "test-board", "/api/v1/files/88");

        when(userDetails.getUsername()).thenReturn(user.getLoginId());
        when(boardRepository.findByBoardUrl("test-board")).thenReturn(Optional.of(board));
        when(boardRepository.saveAndFlush(board))
                .thenThrow(new DataIntegrityViolationException("duplicate key board_name"));

        authenticateUser();
        BusinessException exception;
        try {
            exception = assertThrows(BusinessException.class,
                    () -> boardService.updateBoard("test-board", request, userDetails));
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_BOARD_NAME);
        verify(fileService, never()).replaceBoardIcon(anyLong(), anyLong(), anyLong());
        verify(fileService, never()).deleteFileWithStorageIfAssociated(anyLong(), anyLong(), anyString());
        verify(boardAiInfoRepository, never()).save(any(BoardAiInfo.class));
    }

    @Test
    @DisplayName("게시판 수정 실패 - 저장 시 board_url 충돌이면 DUPLICATE_BOARD_URL")
    void updateBoard_duplicateBoardUrlDuringFlush() {
        UserDetails userDetails = mock(UserDetails.class);
        BoardUpdateRequest request = createBoardUpdateRequest("Test Board", "updated-board", "/api/v1/files/88");

        when(userDetails.getUsername()).thenReturn(user.getLoginId());
        when(boardRepository.findByBoardUrl("test-board")).thenReturn(Optional.of(board));
        when(boardRepository.existsByBoardUrl("updated-board")).thenReturn(false);
        when(boardRepository.saveAndFlush(board))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key",
                        new ConstraintViolationException("duplicate", new java.sql.SQLException("duplicate"),
                                "uk_boards_board_url")));

        authenticateUser();
        BusinessException exception;
        try {
            exception = assertThrows(BusinessException.class,
                    () -> boardService.updateBoard("test-board", request, userDetails));
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_BOARD_URL);
        verify(fileService, never()).replaceBoardIcon(anyLong(), anyLong(), anyLong());
        verify(fileService, never()).deleteFileWithStorageIfAssociated(anyLong(), anyLong(), anyString());
        verify(boardAiInfoRepository, never()).save(any(BoardAiInfo.class));
    }

    @Test
    @DisplayName("게시판 수정 실패 - 제약 메시지를 식별할 수 없으면 DUPLICATE_RESOURCE")
    void updateBoard_duplicateFallbackDuringFlush() {
        UserDetails userDetails = mock(UserDetails.class);
        BoardUpdateRequest request = createBoardUpdateRequest("Updated Board", "test-board", "/api/v1/files/88");

        when(userDetails.getUsername()).thenReturn(user.getLoginId());
        when(boardRepository.findByBoardUrl("test-board")).thenReturn(Optional.of(board));
        when(boardRepository.saveAndFlush(board))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        authenticateUser();
        BusinessException exception;
        try {
            exception = assertThrows(BusinessException.class,
                    () -> boardService.updateBoard("test-board", request, userDetails));
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
        verify(fileService, never()).replaceBoardIcon(anyLong(), anyLong(), anyLong());
        verify(fileService, never()).deleteFileWithStorageIfAssociated(anyLong(), anyLong(), anyString());
        verify(boardAiInfoRepository, never()).save(any(BoardAiInfo.class));
    }

    @Test
    @DisplayName("카테고리 생성은 이름을 trim하고 활성 이름 중복을 사전 검증한다")
    void createCategory_trimsNameAndChecksDuplicateActiveName() {
        CategoryRequest request = categoryRequest("  General  ", 1, "USER");
        when(boardRepository.findByBoardUrl("test-board")).thenReturn(Optional.of(board));
        when(boardCategoryRepository.existsByBoard_BoardIdAndNameAndIsActive(1L, "General", true))
                .thenReturn(false);
        when(boardCategoryRepository.saveAndFlush(any(BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authenticateUser();
        try {
            boardService.createCategory("test-board", request);
        } finally {
            SecurityContextHolder.clearContext();
        }

        ArgumentCaptor<BoardCategory> categoryCaptor = ArgumentCaptor.forClass(BoardCategory.class);
        verify(boardCategoryRepository).saveAndFlush(categoryCaptor.capture());
        assertThat(categoryCaptor.getValue().getName()).isEqualTo("General");
    }

    @Test
    @DisplayName("카테고리 생성은 같은 게시판의 활성 이름 중복을 거부한다")
    void createCategory_duplicateActiveName_throwsDuplicateResource() {
        CategoryRequest request = categoryRequest("General", 1, "USER");
        when(boardRepository.findByBoardUrl("test-board")).thenReturn(Optional.of(board));
        when(boardCategoryRepository.existsByBoard_BoardIdAndNameAndIsActive(1L, "General", true))
                .thenReturn(true);

        authenticateUser();
        BusinessException exception;
        try {
            exception = assertThrows(BusinessException.class,
                    () -> boardService.createCategory("test-board", request));
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
        verify(boardCategoryRepository, never()).saveAndFlush(any(BoardCategory.class));
    }

    @Test
    @DisplayName("카테고리 수정은 자기 자신을 제외하고 활성 이름 중복을 거부한다")
    void updateCategory_duplicateActiveName_throwsDuplicateResource() {
        CategoryRequest request = categoryRequest("General", 1, "USER");
        BoardCategory category = BoardCategory.builder()
                .board(board)
                .name("News")
                .sortOrder(1)
                .minWriteRole("USER")
                .build();
        ReflectionTestUtils.setField(category, "categoryId", 10L);
        when(boardCategoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(boardCategoryRepository.existsByBoard_BoardIdAndNameAndIsActiveAndCategoryIdNot(
                1L,
                "General",
                true,
                10L))
                .thenReturn(true);

        authenticateUser();
        BusinessException exception;
        try {
            exception = assertThrows(BusinessException.class,
                    () -> boardService.updateCategory(10L, request));
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
        verify(boardCategoryRepository, never()).saveAndFlush(any(BoardCategory.class));
    }

    @Test
    @DisplayName("비활성 카테고리 수정은 활성 이름 중복 검증을 건너뛴다")
    void updateCategory_inactiveCategory_skipsActiveNameDuplicateCheck() {
        CategoryRequest request = categoryRequest("General", 1, "USER");
        BoardCategory category = BoardCategory.builder()
                .board(board)
                .name("Archived")
                .sortOrder(1)
                .minWriteRole("USER")
                .build();
        ReflectionTestUtils.setField(category, "categoryId", 10L);
        category.deactivate();
        when(boardCategoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(boardCategoryRepository.saveAndFlush(category)).thenReturn(category);

        authenticateUser();
        try {
            boardService.updateCategory(10L, request);
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(boardCategoryRepository, never()).existsByBoard_BoardIdAndNameAndIsActiveAndCategoryIdNot(
                anyLong(),
                anyString(),
                anyBoolean(),
                anyLong());
        verify(boardCategoryRepository).saveAndFlush(category);
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
        when(boardRepository.saveAndFlush(any(Board.class))).thenReturn(board);
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        doThrow(new BusinessException(ErrorCode.INSUFFICIENT_POINTS))
                .when(pointService)
                .spendPoint(eq(creatorId), eq(500), anyString(), eq(1L), eq("BOARD_CREATE"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.createBoard(creatorId, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_POINTS);
        verify(boardRepository).saveAndFlush(any(Board.class));
        verify(boardCategoryRepository, never()).save(any());
        verify(boardManagerAssignmentService, never()).assignBoardManager(any(), any());
        verify(boardAiInfoRepository, never()).save(any());
    }

    @Test
    @DisplayName("게시판 생성 실패 - 저장 시 board_name 충돌이면 DUPLICATE_BOARD_NAME")
    void createBoard_duplicateBoardNameDuringFlush() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(boardRepository.existsByBoardName(request.getBoardName())).thenReturn(false, true);
        when(boardRepository.existsByBoardUrl(request.getBoardUrl())).thenReturn(false);
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        when(boardRepository.saveAndFlush(any(Board.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key board_name"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.createBoard(creatorId, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_BOARD_NAME);
        verify(pointService, never()).spendPoint(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("게시판 생성 실패 - 저장 시 board_url 충돌이면 DUPLICATE_BOARD_URL")
    void createBoard_duplicateBoardUrlDuringFlush() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(boardRepository.existsByBoardName(request.getBoardName())).thenReturn(false);
        when(boardRepository.existsByBoardUrl(request.getBoardUrl())).thenReturn(false, true);
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        when(boardRepository.saveAndFlush(any(Board.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key board_url"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.createBoard(creatorId, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_BOARD_URL);
        verify(pointService, never()).spendPoint(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("게시판 생성 실패 - 제약 메시지를 식별할 수 없으면 DUPLICATE_RESOURCE")
    void createBoard_duplicateFallbackDuringFlush() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(boardRepository.existsByBoardName(request.getBoardName())).thenReturn(false);
        when(boardRepository.existsByBoardUrl(request.getBoardUrl())).thenReturn(false);
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        when(boardRepository.saveAndFlush(any(Board.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.createBoard(creatorId, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("게시판 생성 실패 - 중첩 ConstraintViolationException의 제약명으로 board_url 충돌을 판별한다")
    void createBoard_duplicateBoardUrlByConstraintName() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(boardRepository.existsByBoardName(request.getBoardName())).thenReturn(false);
        when(boardRepository.existsByBoardUrl(request.getBoardUrl())).thenReturn(false);
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        when(boardRepository.saveAndFlush(any(Board.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key",
                        new ConstraintViolationException("duplicate", new java.sql.SQLException("duplicate"), "uk_boards_board_url")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.createBoard(creatorId, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_BOARD_URL);
    }

    @Test
    @DisplayName("게시판 생성 실패 - 중첩 ConstraintViolationException의 제약명으로 board_name 충돌을 판별한다")
    void createBoard_duplicateBoardNameByConstraintName() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(boardRepository.existsByBoardName(request.getBoardName())).thenReturn(false);
        when(boardRepository.existsByBoardUrl(request.getBoardUrl())).thenReturn(false);
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        when(boardRepository.saveAndFlush(any(Board.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key",
                        new ConstraintViolationException("duplicate", new java.sql.SQLException("duplicate"), "boards_board_name_key")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.createBoard(creatorId, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_BOARD_NAME);
    }

    @Test
    @DisplayName("게시판 생성 실패 - legacy 제약명으로도 board_url 충돌을 판별한다")
    void createBoard_duplicateBoardUrlByLegacyConstraintName() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(boardRepository.existsByBoardName(request.getBoardName())).thenReturn(false);
        when(boardRepository.existsByBoardUrl(request.getBoardUrl())).thenReturn(false);
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        when(boardRepository.saveAndFlush(any(Board.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key",
                        new ConstraintViolationException("duplicate", new java.sql.SQLException("duplicate"), "boards_board_url_key")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.createBoard(creatorId, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_BOARD_URL);
    }

    @Test
    @DisplayName("인기 게시판 목록 조회 성공")
    void getTopBoards_success() {
        // given
        when(boardRepository.findTopPublicBoardIdsByPostCount(any())).thenReturn(Collections.singletonList(1L));
        when(boardRepository.findByBoardIdIn(List.of(1L))).thenReturn(Collections.singletonList(board));
        when(postRepository.countActiveByBoardIds(List.of(1L))).thenReturn(List.of(new PostRepository.BoardPostCountProjection() {
            @Override
            public Long getBoardId() {
                return 1L;
            }

            @Override
            public Long getPostCount() {
                return 37L;
            }
        }));

        // when
        List<BoardListResponse> boards = boardService.getTopBoards((UserDetails) null);

        // then
        assertThat(boards).hasSize(1);
        assertThat(boards.get(0).getPostCount()).isEqualTo(37L);
        verify(boardRepository).findTopPublicBoardIdsByPostCount(any());
        verify(boardRepository).findByBoardIdIn(List.of(1L));
    }

    @Test
    @DisplayName("일반 로그인 사용자는 공개 인기 게시판 전용 쿼리를 사용한다")
    void getTopBoards_authenticatedUsesPublicQueryWhenUserHasNoElevatedAccess() {
        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn(user.getLoginId());
        when(adminRepository.existsByUserAndIsActive(user, true)).thenReturn(false);
        when(boardRepository.findTopPublicBoardIdsByPostCount(any())).thenReturn(Collections.singletonList(1L));
        when(boardRepository.findByBoardIdIn(List.of(1L))).thenReturn(Collections.singletonList(board));

        List<BoardListResponse> boards = boardService.getTopBoards(userDetails);

        assertThat(boards).extracting(BoardListResponse::getBoardUrl).containsExactly("test-board");
        verify(adminRepository).existsByUserAndIsActive(user, true);
        verify(boardRepository).findTopPublicBoardIdsByPostCount(any());
        verify(boardRepository).findByBoardIdIn(List.of(1L));
        verify(boardRepository, never()).findTopBoardIdsByPostCount(any());
        verify(boardRepository, never()).findTopReadableBoardIdsByPostCount(any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("userId 기반 인기 게시판 조회는 principal 해석 없이 사용자 ID로 조회한다")
    void getTopBoards_userIdUsesPublicQueryWhenUserHasNoElevatedAccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRepository.existsByUserAndIsActive(user, true)).thenReturn(false);
        when(boardRepository.findTopPublicBoardIdsByPostCount(any())).thenReturn(Collections.singletonList(1L));
        when(boardRepository.findByBoardIdIn(List.of(1L))).thenReturn(Collections.singletonList(board));

        List<BoardListResponse> boards = boardService.getTopBoardsByUserId(1L);

        assertThat(boards).extracting(BoardListResponse::getBoardUrl).containsExactly("test-board");
        verify(userRepository).findById(1L);
        verify(boardRepository).findTopPublicBoardIdsByPostCount(any());
        verify(boardRepository, never()).findTopReadableBoardIdsByPostCount(any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("userId 기반 인기 게시판 조회는 없는 사용자면 USER_NOT_FOUND를 반환한다")
    void getTopBoards_userIdNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.getTopBoardsByUserId(99L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        verify(boardRepository, never()).findTopPublicBoardIdsByPostCount(any());
        verify(boardRepository, never()).findTopReadableBoardIdsByPostCount(any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("userId 기반 인기 게시판 조회는 권한 사용자의 readable 게시판 쿼리를 사용한다")
    void getTopBoards_userIdBoardAdminUsesReadableQuery() {
        Board privateBoard = Board.builder()
                .boardName("Private Board")
                .boardUrl("private-board")
                .creator(user)
                .isPublic(false)
                .build();
        ReflectionTestUtils.setField(privateBoard, "boardId", 2L);
        ReflectionTestUtils.setField(privateBoard, "isActive", true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRepository.existsByUserAndIsActive(user, true)).thenReturn(true);
        when(boardRepository.findTopReadableBoardIdsByPostCount(eq(user), eq(false), any())).thenReturn(List.of(2L));
        when(boardRepository.findByBoardIdIn(List.of(2L))).thenReturn(List.of(privateBoard));

        List<BoardListResponse> boards = boardService.getTopBoardsByUserId(1L);

        assertThat(boards).extracting(BoardListResponse::getBoardUrl).containsExactly("private-board");
        verify(userRepository).findById(1L);
        verify(boardRepository).findTopReadableBoardIdsByPostCount(eq(user), eq(false), any());
        verify(boardRepository, never()).findTopPublicBoardIdsByPostCount(any());
    }

    @Test
    @DisplayName("게시판 관리자는 읽을 수 있는 인기 게시판 조회 쿼리를 사용한다")
    void getTopBoards_boardAdminUsesReadableQuery() {
        Board privateBoard = Board.builder()
                .boardName("Private Board")
                .boardUrl("private-board")
                .creator(user)
                .isPublic(false)
                .build();
        ReflectionTestUtils.setField(privateBoard, "boardId", 2L);
        ReflectionTestUtils.setField(privateBoard, "isActive", true);

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn(user.getLoginId());
        when(adminRepository.existsByUserAndIsActive(user, true)).thenReturn(true);
        when(boardRepository.findTopReadableBoardIdsByPostCount(eq(user), eq(false), any())).thenReturn(List.of(2L));
        when(boardRepository.findByBoardIdIn(List.of(2L))).thenReturn(List.of(privateBoard));

        List<BoardListResponse> boards = boardService.getTopBoards(userDetails);

        assertThat(boards).extracting(BoardListResponse::getBoardUrl).containsExactly("private-board");
        verify(adminRepository).existsByUserAndIsActive(user, true);
        verify(boardRepository).findTopReadableBoardIdsByPostCount(eq(user), eq(false), any());
        verify(boardRepository, never()).findTopPublicBoardIdsByPostCount(any());
        verify(boardRepository, never()).findTopBoardIdsByPostCount(any());
    }

    @Test
    @DisplayName("권한 사용자는 읽을 수 있는 비공개 게시판도 인기 게시판에 포함한다")
    void getTopBoards_privilegedUserIncludesReadablePrivateBoard() {
        Board privateBoard = Board.builder()
                .boardName("Private Board")
                .boardUrl("private-board")
                .creator(user)
                .isPublic(false)
                .build();
        ReflectionTestUtils.setField(privateBoard, "boardId", 2L);
        ReflectionTestUtils.setField(privateBoard, "isActive", true);
        user.grantSuperAdminRole();

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn(user.getLoginId());
        when(boardRepository.findTopReadableBoardIdsByPostCount(eq(user), eq(true), any())).thenReturn(List.of(2L));
        when(boardRepository.findByBoardIdIn(List.of(2L))).thenReturn(List.of(privateBoard));

        List<BoardListResponse> boards = boardService.getTopBoards(userDetails);

        assertThat(boards).extracting(BoardListResponse::getBoardUrl).containsExactly("private-board");
        verify(boardRepository).findTopReadableBoardIdsByPostCount(eq(user), eq(true), any());
        verify(boardRepository).findByBoardIdIn(List.of(2L));
    }

    @Test
    @DisplayName("게시판 상세 조회 성공")
    void getBoardDetails_success() {
        // given
        String boardUrl = "test-board";
        when(boardRepository.findByBoardUrl(boardUrl)).thenReturn(Optional.of(board));

        // when
        BoardDetailResponse response = boardService.getBoardDetails(boardUrl, null);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getBoardName()).isEqualTo("Test Board");
    }

    @Test
    @DisplayName("게시판 공지 요약 조회 성공")
    void getNoticeSummaries_success() {
        Post notice = Post.builder()
                .board(board)
                .user(user)
                .title("Notice")
                .contents("Notice contents")
                .isNotice(true)
                .build();
        ReflectionTestUtils.setField(notice, "postId", 10L);

        when(boardRepository.findByBoardUrl("test-board")).thenReturn(Optional.of(board));
        when(postService.getNotices(1L, null, false)).thenReturn(List.of(notice));

        List<PostSummary> summaries = boardService.getNoticeSummaries("test-board", null);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).getTitle()).isEqualTo("Notice");
        verify(postService).getNotices(1L, null, false);
    }

    @Test
    @DisplayName("비공개 게시판 공지 요약은 권한이 없으면 차단한다")
    void getNoticeSummaries_privateBoardWithoutAccess_throwsBoardNotFound() {
        Board privateBoard = Board.builder()
                .boardName("Private Board")
                .boardUrl("private-board")
                .creator(user)
                .isPublic(false)
                .build();
        ReflectionTestUtils.setField(privateBoard, "boardId", 2L);
        ReflectionTestUtils.setField(privateBoard, "isActive", true);

        when(boardRepository.findByBoardUrl("private-board")).thenReturn(Optional.of(privateBoard));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.getNoticeSummaries("private-board", null));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BOARD_NOT_FOUND);
        verify(postService, never()).getNotices(anyLong(), any(), anyBoolean());
    }

    @Test
    @DisplayName("비활성 게시판 공지 요약은 권한이 없으면 차단한다")
    void getNoticeSummaries_inactiveBoardWithoutAccess_throwsBoardNotFound() {
        Board inactiveBoard = Board.builder()
                .boardName("Inactive Board")
                .boardUrl("inactive-board")
                .creator(user)
                .isPublic(true)
                .build();
        ReflectionTestUtils.setField(inactiveBoard, "boardId", 3L);
        ReflectionTestUtils.setField(inactiveBoard, "isActive", false);

        when(boardRepository.findByBoardUrl("inactive-board")).thenReturn(Optional.of(inactiveBoard));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.getNoticeSummaries("inactive-board", null));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BOARD_NOT_FOUND);
        verify(postService, never()).getNotices(anyLong(), any(), anyBoolean());
    }

    @Test
    @DisplayName("문의 게시판 공지 요약 조회는 차단한다")
    void getNoticeSummaries_inquiryBoard_throwsBoardNotFound() {
        Board inquiryBoard = Board.builder()
                .boardName("Inquiry")
                .boardUrl("inquiry")
                .creator(user)
                .isPublic(false)
                .build();
        ReflectionTestUtils.setField(inquiryBoard, "boardId", 4L);
        ReflectionTestUtils.setField(inquiryBoard, "isActive", true);

        when(boardRepository.findByBoardUrl("inquiry")).thenReturn(Optional.of(inquiryBoard));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.getNoticeSummaries("inquiry", null));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BOARD_NOT_FOUND);
        verify(postService, never()).getNotices(anyLong(), any(), anyBoolean());
    }

    @Test
    @DisplayName("게시판 구독 해제 성공")
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
    @DisplayName("읽을 수 없게 된 게시판도 기존 구독은 해지할 수 있다")
    void unsubscribeBoard_allowsHiddenBoardSubscription() {
        Board hiddenBoard = Board.builder()
                .boardName("Hidden Board")
                .boardUrl("hidden-board")
                .creator(user)
                .isPublic(false)
                .build();
        ReflectionTestUtils.setField(hiddenBoard, "boardId", 2L);

        BoardSubscription subscription = BoardSubscription.builder()
                .user(user)
                .board(hiddenBoard)
                .role("MEMBER")
                .sortOrder(1)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("hidden-board")).thenReturn(Optional.of(hiddenBoard));
        when(boardSubscriptionRepository.findById(new BoardSubscriptionId(1L, 2L)))
                .thenReturn(Optional.of(subscription));

        boardService.unsubscribeBoard(1L, "hidden-board");

        verify(boardSubscriptionRepository).delete(subscription);
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
        when(boardSubscriptionRepository.findVisibleByUserOrderBySortOrderAsc(
                user, false, PageRequest.of(0, 1)))
                .thenReturn(new PageImpl<>(List.of(subscription), PageRequest.of(0, 1), 1));
        when(boardSubscriptionRepository.findByUserAndBoardIn(user, List.of(board))).thenReturn(List.of(subscription));

        var result = boardService.getMySubscriptions(1L, PageRequest.of(0, 1));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).isSubscribed()).isTrue();
    }

    @Test
    @DisplayName("구독 순서 변경은 전체 목록이 일치할 때만 1..N으로 재기록한다")
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

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findReorderableByUserAndBoardUrlIn(
                user,
                new LinkedHashSet<>(List.of("second-board", "test-board")),
                false))
                .thenReturn(List.of(firstSubscription, secondSubscription));
        when(boardSubscriptionRepository.findMaxSortOrder(user)).thenReturn(2);

        boardService.updateSubscriptionOrder(1L, List.of("second-board", "test-board"));

        assertThat(firstSubscription.getSortOrder()).isEqualTo(2);
        assertThat(secondSubscription.getSortOrder()).isEqualTo(1);
        InOrder inOrder = inOrder(boardSubscriptionRepository);
        inOrder.verify(boardSubscriptionRepository).saveAll(List.of(firstSubscription, secondSubscription));
        inOrder.verify(boardSubscriptionRepository).flush();
        inOrder.verify(boardSubscriptionRepository).saveAll(List.of(firstSubscription, secondSubscription));
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

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findReorderableByUserAndBoardUrlIn(
                user,
                new LinkedHashSet<>(List.of("test-board", "missing-board")),
                false))
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

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findReorderableByUserAndBoardUrlIn(
                user,
                new LinkedHashSet<>(List.of("second-board", "test-board")),
                false))
                .thenReturn(List.of(activeSubscription, anotherActiveSubscription));
        when(boardSubscriptionRepository.findMaxSortOrder(user)).thenReturn(2);

        boardService.updateSubscriptionOrder(1L, List.of("second-board", "test-board"));

        assertThat(activeSubscription.getSortOrder()).isEqualTo(2);
        assertThat(anotherActiveSubscription.getSortOrder()).isEqualTo(1);
        InOrder inOrder = inOrder(boardSubscriptionRepository);
        inOrder.verify(boardSubscriptionRepository).saveAll(List.of(activeSubscription, anotherActiveSubscription));
        inOrder.verify(boardSubscriptionRepository).flush();
        inOrder.verify(boardSubscriptionRepository).saveAll(List.of(activeSubscription, anotherActiveSubscription));
    }

    @Test
    @DisplayName("구독 순서 변경은 임시 sort_order를 거쳐 유니크 충돌을 피한다")
    void updateSubscriptionOrder_usesTemporarySortOrdersBeforeFinalOrder() {
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
        List<List<Integer>> sortOrderSnapshots = new ArrayList<>();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findReorderableByUserAndBoardUrlIn(
                user,
                new LinkedHashSet<>(List.of("second-board", "test-board")),
                false))
                .thenReturn(List.of(firstSubscription, secondSubscription));
        when(boardSubscriptionRepository.findMaxSortOrder(user)).thenReturn(2);
        when(boardSubscriptionRepository.saveAll(any()))
                .thenAnswer(invocation -> {
                    List<BoardSubscription> subscriptions = invocation.getArgument(0);
                    sortOrderSnapshots.add(subscriptions.stream()
                            .map(BoardSubscription::getSortOrder)
                            .toList());
                    return subscriptions;
                });

        boardService.updateSubscriptionOrder(1L, List.of("second-board", "test-board"));

        assertThat(sortOrderSnapshots).containsExactly(List.of(6, 5), List.of(2, 1));
    }

    @Test
    @DisplayName("구독 순서 변경 충돌은 중복 구독으로 오인하지 않는다")
    void updateSubscriptionOrder_conflictMapsToDuplicateResource() {
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

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findReorderableByUserAndBoardUrlIn(
                user,
                new LinkedHashSet<>(List.of("second-board", "test-board")),
                false))
                .thenReturn(List.of(firstSubscription, secondSubscription));
        when(boardSubscriptionRepository.findMaxSortOrder(user)).thenReturn(2);
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(boardSubscriptionRepository).flush();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.updateSubscriptionOrder(1L, List.of("second-board", "test-board")));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
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
        when(userDetails.getUsername()).thenReturn(user.getLoginId());
        when(userRepository.findByLoginId(user.getLoginId())).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrlForUpdate("inquiry")).thenReturn(Optional.empty());
        when(userRepository.findUsableSuperAdmins()).thenReturn(List.of(activeSuperAdmin));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        when(boardRepository.existsByBoardName(anyString())).thenReturn(false);
        when(boardRepository.saveAndFlush(any(Board.class))).thenAnswer(invocation -> {
            Board savedBoard = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedBoard, "boardId", 2L);
            return savedBoard;
        });
        when(boardCategoryRepository.saveAndFlush(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boardService.ensureInquiryBoard(userDetails, "custom-inquiry-url");

        ArgumentCaptor<Board> boardCaptor = ArgumentCaptor.forClass(Board.class);
        ArgumentCaptor<BoardCategory> categoryCaptor = ArgumentCaptor.forClass(BoardCategory.class);
        verify(boardRepository).saveAndFlush(boardCaptor.capture());
        verify(boardCategoryRepository, org.mockito.Mockito.atLeastOnce()).saveAndFlush(categoryCaptor.capture());
        assertThat(boardCaptor.getValue().getCreator()).isEqualTo(activeSuperAdmin);
        assertThat(boardCaptor.getValue().getBoardName()).isEqualTo("문의");
        assertThat(boardCaptor.getValue().getDescription()).isEqualTo("운영진에게 문의를 남기는 비공개 게시판입니다.");
        assertThat(categoryCaptor.getAllValues())
                .extracting(BoardCategory::getName)
                .containsOnly("일반");
        verify(userRepository, org.mockito.Mockito.atLeastOnce()).findUsableSuperAdmins();
        verify(boardManagerAssignmentService, org.mockito.Mockito.atLeastOnce())
                .assignBoardManager(boardCaptor.getValue(), activeSuperAdmin);
    }

    @Test
    @DisplayName("문의 게시판 보정은 공개 설정과 중복 기본 카테고리 및 관리자 행을 정리한다")
    void ensureInquiryBoard_normalizesExistingBoardState() {
        UserDetails userDetails = mock(UserDetails.class);
        User superAdmin = User.builder()
                .loginId("super-admin")
                .password("password")
                .email("super@test.com")
                .displayName("Super Admin")
                .build();
        ReflectionTestUtils.setField(superAdmin, "userId", 2L);
        superAdmin.grantSuperAdminRole();
        ReflectionTestUtils.setField(board, "isPublic", true);
        ReflectionTestUtils.setField(board, "agentUseYn", true);

        com.weedrice.whiteboard.domain.board.entity.BoardCategory defaultCategory = com.weedrice.whiteboard.domain.board.entity.BoardCategory.builder()
                .board(board)
                .name("일반")
                .sortOrder(2)
                .build();
        ReflectionTestUtils.setField(defaultCategory, "categoryId", 10L);

        com.weedrice.whiteboard.domain.board.entity.BoardCategory duplicateCategory = com.weedrice.whiteboard.domain.board.entity.BoardCategory.builder()
                .board(board)
                .name("일반")
                .sortOrder(3)
                .build();
        ReflectionTestUtils.setField(duplicateCategory, "categoryId", 11L);

        when(userDetails.getUsername()).thenReturn(user.getLoginId());
        when(userRepository.findByLoginId(user.getLoginId())).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrlForUpdate("inquiry")).thenReturn(Optional.of(board));
        when(userRepository.findUsableSuperAdmins()).thenReturn(List.of(superAdmin));
        when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true))
                .thenReturn(List.of(defaultCategory, duplicateCategory));

        boardService.ensureInquiryBoard(userDetails, "custom-inquiry-url");

        assertThat(board.getIsPublic()).isFalse();
        assertThat(board.isAgentEnabled()).isFalse();
        assertThat(defaultCategory.getSortOrder()).isEqualTo(1);
        assertThat(duplicateCategory.getIsActive()).isFalse();
        verify(boardCategoryRepository, never()).saveAndFlush(any());
        verify(boardManagerAssignmentService).assignBoardManager(board, superAdmin);
    }

    @Test
    @DisplayName("문의 게시판 보정은 기존 활성 관리자를 먼저 비활성화한 뒤 목표 관리자를 재활성화한다")
    void ensureInquiryBoard_reactivatesCanonicalManagerAfterDeactivatingWrongManager() {
        UserDetails userDetails = mock(UserDetails.class);
        User superAdmin = User.builder()
                .loginId("super-admin")
                .password("password")
                .email("super@test.com")
                .displayName("Super Admin")
                .build();
        ReflectionTestUtils.setField(superAdmin, "userId", 2L);
        superAdmin.grantSuperAdminRole();

        when(userDetails.getUsername()).thenReturn(user.getLoginId());
        when(userRepository.findByLoginId(user.getLoginId())).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrlForUpdate("inquiry")).thenReturn(Optional.of(board));
        when(userRepository.findUsableSuperAdmins()).thenReturn(List.of(superAdmin));
        when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true))
                .thenReturn(Collections.emptyList());
        when(boardCategoryRepository.saveAndFlush(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boardService.ensureInquiryBoard(userDetails, "custom-inquiry-url");

        verify(boardManagerAssignmentService).assignBoardManager(board, superAdmin);
    }

    @Test
    @DisplayName("문의 게시판 생성 중 URL 중복 예외가 나면 잠금을 다시 잡아 기존 보드를 재사용한다")
    void ensureInquiryBoard_reusesBoardAfterDuplicateCreateConflict() {
        UserDetails userDetails = mock(UserDetails.class);
        User superAdmin = User.builder()
                .loginId("super-admin")
                .password("password")
                .email("super@test.com")
                .displayName("Super Admin")
                .build();
        ReflectionTestUtils.setField(superAdmin, "userId", 2L);
        superAdmin.grantSuperAdminRole();

        when(userDetails.getUsername()).thenReturn(user.getLoginId());
        when(userRepository.findByLoginId(user.getLoginId())).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrlForUpdate("inquiry"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(board));
        when(userRepository.findUsableSuperAdmins()).thenReturn(List.of(superAdmin));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        when(boardRepository.existsByBoardName(anyString())).thenReturn(false);
        when(boardRepository.saveAndFlush(any(Board.class)))
                .thenThrow(new DataIntegrityViolationException("uk_boards_board_url"));
        when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true))
                .thenReturn(Collections.emptyList());
        when(boardCategoryRepository.saveAndFlush(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boardService.ensureInquiryBoard(userDetails, "custom-inquiry-url");

        verify(boardRepository).saveAndFlush(any(Board.class));
        verify(boardRepository, org.mockito.Mockito.times(2)).findByBoardUrlForUpdate("inquiry");
        verify(boardCategoryRepository).saveAndFlush(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class));
        verify(boardManagerAssignmentService).assignBoardManager(board, superAdmin);
    }

    @Test
    @DisplayName("문의 게시판 생성 중 이름 중복 예외가 나도 기존 보드를 다시 잠가 재사용한다")
    void ensureInquiryBoard_reusesBoardAfterDuplicateNameConflict() {
        UserDetails userDetails = mock(UserDetails.class);
        User superAdmin = User.builder()
                .loginId("super-admin")
                .password("password")
                .email("super@test.com")
                .displayName("Super Admin")
                .build();
        ReflectionTestUtils.setField(superAdmin, "userId", 2L);
        superAdmin.grantSuperAdminRole();

        when(userDetails.getUsername()).thenReturn(user.getLoginId());
        when(userRepository.findByLoginId(user.getLoginId())).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrlForUpdate("inquiry"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(board));
        when(userRepository.findUsableSuperAdmins()).thenReturn(List.of(superAdmin));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        when(boardRepository.existsByBoardName(anyString())).thenReturn(false);
        when(boardRepository.saveAndFlush(any(Board.class)))
                .thenThrow(new DataIntegrityViolationException("uk_boards_board_name"));
        when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true))
                .thenReturn(Collections.emptyList());
        when(boardCategoryRepository.saveAndFlush(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boardService.ensureInquiryBoard(userDetails, "custom-inquiry-url");

        verify(boardRepository).saveAndFlush(any(Board.class));
        verify(boardRepository, org.mockito.Mockito.times(2)).findByBoardUrlForUpdate("inquiry");
        verify(boardCategoryRepository).saveAndFlush(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class));
        verify(boardManagerAssignmentService).assignBoardManager(board, superAdmin);
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

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findReorderableByUserAndBoardUrlIn(
                user,
                new LinkedHashSet<>(List.of("test-board")),
                false))
                .thenReturn(List.of(visibleSubscription));
        when(boardSubscriptionRepository.findMaxSortOrder(user)).thenReturn(1);

        boardService.updateSubscriptionOrder(1L, List.of("test-board"));

        assertThat(visibleSubscription.getSortOrder()).isEqualTo(1);
        assertThat(hiddenSubscription.getSortOrder()).isEqualTo(2);
        InOrder inOrder = inOrder(boardSubscriptionRepository);
        inOrder.verify(boardSubscriptionRepository).saveAll(List.of(visibleSubscription));
        inOrder.verify(boardSubscriptionRepository).flush();
        inOrder.verify(boardSubscriptionRepository).saveAll(List.of(visibleSubscription));
    }

    @Test
    @DisplayName("구독 순서 변경은 요청된 접근 가능 구독만 부분적으로 재정렬할 수 있다")
    void updateSubscriptionOrder_reordersRequestedSubsetOnly() {
        Board secondBoard = Board.builder()
                .boardName("Second Board")
                .boardUrl("second-board")
                .creator(user)
                .build();
        ReflectionTestUtils.setField(secondBoard, "boardId", 2L);

        Board thirdBoard = Board.builder()
                .boardName("Third Board")
                .boardUrl("third-board")
                .creator(user)
                .build();
        ReflectionTestUtils.setField(thirdBoard, "boardId", 3L);

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
        BoardSubscription thirdSubscription = BoardSubscription.builder()
                .user(user)
                .board(thirdBoard)
                .role("MEMBER")
                .sortOrder(3)
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findReorderableByUserAndBoardUrlIn(
                user,
                new LinkedHashSet<>(List.of("third-board", "second-board")),
                false))
                .thenReturn(List.of(secondSubscription, thirdSubscription));
        when(boardSubscriptionRepository.findMaxSortOrder(user)).thenReturn(3);

        boardService.updateSubscriptionOrder(1L, List.of("third-board", "second-board"));

        assertThat(firstSubscription.getSortOrder()).isEqualTo(1);
        assertThat(secondSubscription.getSortOrder()).isEqualTo(3);
        assertThat(thirdSubscription.getSortOrder()).isEqualTo(2);
        InOrder inOrder = inOrder(boardSubscriptionRepository);
        inOrder.verify(boardSubscriptionRepository).saveAll(List.of(secondSubscription, thirdSubscription));
        inOrder.verify(boardSubscriptionRepository).flush();
        inOrder.verify(boardSubscriptionRepository).saveAll(List.of(secondSubscription, thirdSubscription));
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
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findVisibleByUserOrderBySortOrderAsc(
                user, false, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(visibleSubscription), PageRequest.of(0, 10), 1));
        when(boardSubscriptionRepository.findByUserAndBoardIn(user, List.of(board)))
                .thenReturn(List.of(visibleSubscription));

        var result = boardService.getMySubscriptions(1L, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getBoardUrl()).isEqualTo("test-board");
    }

    @Test
    @DisplayName("내 구독 목록은 includeUnavailable 요청 시 숨겨진 구독을 tombstone 으로 노출한다")
    void getMySubscriptions_includeUnavailableReturnsTombstones() {
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

        BoardSubscription hiddenSubscription = BoardSubscription.builder()
                .user(user)
                .board(hiddenBoard)
                .role("MEMBER")
                .sortOrder(1)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findByUserOrderBySortOrderAsc(user, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(hiddenSubscription), PageRequest.of(0, 10), 1));

        var result = boardService.getMySubscriptions(1L, PageRequest.of(0, 10), true);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getBoardName()).isNull();
        assertThat(result.getContent().get(0).getDescription()).isNull();
        assertThat(result.getContent().get(0).isSubscriptionAccessible()).isFalse();
        assertThat(result.getContent().get(0).getBoardUrl()).isEqualTo("hidden-board");
    }

    @Test
    @DisplayName("내 구독 목록은 includeUnavailable 요청 시 필터링용 관리자 권한을 페이지 단위로 일괄 조회한다")
    void getMySubscriptions_includeUnavailableUsesBulkAdminAccessLookupForFiltering() {
        User hiddenBoardCreator = User.builder()
                .loginId("hidden-owner")
                .password("password")
                .email("hidden@test.com")
                .displayName("Hidden Owner")
                .build();
        ReflectionTestUtils.setField(hiddenBoardCreator, "userId", 99L);

        Board adminReadableBoard = Board.builder()
                .boardName("Admin Hidden")
                .boardUrl("admin-hidden")
                .creator(hiddenBoardCreator)
                .isPublic(false)
                .build();
        ReflectionTestUtils.setField(adminReadableBoard, "boardId", 3L);
        Board adminInactiveBoard = Board.builder()
                .boardName("Admin Inactive")
                .boardUrl("admin-inactive")
                .creator(hiddenBoardCreator)
                .isPublic(true)
                .build();
        ReflectionTestUtils.setField(adminInactiveBoard, "boardId", 4L);
        adminInactiveBoard.deactivate();
        Board unreadableBoard = Board.builder()
                .boardName("Unreadable")
                .boardUrl("unreadable")
                .creator(hiddenBoardCreator)
                .isPublic(false)
                .build();
        ReflectionTestUtils.setField(unreadableBoard, "boardId", 5L);

        BoardSubscription adminReadableSubscription = BoardSubscription.builder()
                .user(user)
                .board(adminReadableBoard)
                .role("MEMBER")
                .sortOrder(1)
                .build();
        BoardSubscription adminInactiveSubscription = BoardSubscription.builder()
                .user(user)
                .board(adminInactiveBoard)
                .role("MEMBER")
                .sortOrder(2)
                .build();
        BoardSubscription unreadableSubscription = BoardSubscription.builder()
                .user(user)
                .board(unreadableBoard)
                .role("MEMBER")
                .sortOrder(3)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findByUserOrderBySortOrderAsc(user, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(
                        List.of(adminReadableSubscription, adminInactiveSubscription, unreadableSubscription),
                        PageRequest.of(0, 10),
                        3));
        when(adminRepository.findByUserAndBoard_BoardIdInAndIsActive(eq(user), any(), eq(true)))
                .thenReturn(List.of(
                        Admin.builder().user(user).board(adminReadableBoard).role("BOARD_ADMIN").build(),
                        Admin.builder().user(user).board(adminInactiveBoard).role("BOARD_ADMIN").build()));

        var result = boardService.getMySubscriptions(1L, PageRequest.of(0, 10), true);

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getBoardName()).isEqualTo("Admin Hidden");
        assertThat(result.getContent().get(0).isSubscriptionAccessible()).isTrue();
        assertThat(result.getContent().get(1).getBoardName()).isEqualTo("Admin Inactive");
        assertThat(result.getContent().get(1).isSubscriptionAccessible()).isTrue();
        assertThat(result.getContent().get(2).getBoardName()).isNull();
        assertThat(result.getContent().get(2).isSubscriptionAccessible()).isFalse();
        verify(adminRepository).findByUserAndBoard_BoardIdInAndIsActive(
                eq(user),
                argThat(boardIds -> boardIds.containsAll(List.of(3L, 4L, 5L)) && boardIds.size() == 3),
                eq(true));
        verify(adminRepository, never()).existsByUserAndBoardAndIsActive(eq(user), any(Board.class), eq(true));
    }

    private void authenticateUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        CustomUserDetails principal = new CustomUserDetails(1L, user.getLoginId(), "password", Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private CategoryRequest categoryRequest(String name, Integer sortOrder, String minWriteRole) {
        CategoryRequest request = new CategoryRequest();
        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "sortOrder", sortOrder);
        ReflectionTestUtils.setField(request, "minWriteRole", minWriteRole);
        return request;
    }

    private BoardUpdateRequest createBoardUpdateRequest(String boardName, String boardUrl, String iconUrl) {
        BoardUpdateRequest request = new BoardUpdateRequest();
        ReflectionTestUtils.setField(request, "boardName", boardName);
        ReflectionTestUtils.setField(request, "description", "Updated Description");
        ReflectionTestUtils.setField(request, "boardUrl", boardUrl);
        ReflectionTestUtils.setField(request, "iconUrl", iconUrl);
        ReflectionTestUtils.setField(request, "sortOrder", 1);
        ReflectionTestUtils.setField(request, "isActive", true);
        ReflectionTestUtils.setField(request, "isPublic", true);
        return request;
    }
}
