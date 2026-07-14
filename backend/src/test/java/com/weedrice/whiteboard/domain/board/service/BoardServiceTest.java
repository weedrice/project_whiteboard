package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.admin.service.AdminEligibleUserService;
import com.weedrice.whiteboard.domain.admin.service.BoardManagerAssignmentService;
import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.board.dto.AdminBoardResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardDetailResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardManagerCandidateResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryRequest;
import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import com.weedrice.whiteboard.domain.board.dto.SubscriptionBoardResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardAiInfo;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscriptionId;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.repository.DraftPostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostLatestReadService;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchEventPublisher;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.ofEpochMilli(1_770_000_000_000L),
            ZoneOffset.UTC);

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
    @Mock
    private SemanticSearchEventPublisher semanticSearchEventPublisher;
    @Mock
    private BoardVisitService boardVisitService;
    private BoardResponseReadService boardResponseReadService;
    private BoardResponseAssembler boardResponseAssembler;

    private BoardService boardService;
    private BoardApplicationService boardApplicationService;
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
                postService,
                boardVisitService);
        BoardIconAttachmentService boardIconAttachmentService = new BoardIconAttachmentService(fileService);
        BoardCreationBillingService boardCreationBillingService = new BoardCreationBillingService(
                pointService,
                globalConfigService);
        BoardAiInfoService boardAiInfoService = new BoardAiInfoService(boardAiInfoRepository);
        BoardCreationInitializer boardCreationInitializer = new BoardCreationInitializer(
                boardAiInfoService,
                boardCategoryRepository,
                boardManagerAssignmentService);
        BoardProvisioningService provisioningService = new BoardProvisioningService(
                boardRepository,
                boardCategoryRepository,
                boardSubscriptionRepository,
                userRepository,
                adminEligibleUserService,
                boardManagerAssignmentService,
                boardAccessPolicy,
                FIXED_CLOCK);
        BoardCommandService boardCommandService = new BoardCommandService(
                boardRepository,
                userRepository,
                adminEligibleUserService,
                boardAccessPolicy);
        BoardProvisioningSideEffectService provisioningSideEffectService = new BoardProvisioningSideEffectService(
                boardIconAttachmentService,
                boardCreationBillingService,
                boardCreationInitializer,
                boardAiInfoService,
                semanticSearchEventPublisher);
        BoardSubscriptionService subscriptionService = new BoardSubscriptionService(
                boardRepository,
                boardSubscriptionRepository,
                new UserWritableResolver(userRepository, sanctionService),
                boardAccessPolicy,
                new BoardSubscriptionWritePolicy(boardSubscriptionRepository));
        BoardCategoryService categoryService = new BoardCategoryService(
                new BoardCategoryMutationResolver(
                        boardRepository,
                        boardCategoryRepository,
                        userRepository,
                        boardAccessPolicy),
                boardCategoryRepository,
                new BoardCategoryNameConflictPolicy(boardCategoryRepository),
                new BoardCategoryDefaultCommand(boardCategoryRepository),
                new BoardCategoryResponseAssembler());
        boardService = new BoardService(
                queryService,
                provisioningService,
                boardCommandService,
                provisioningSideEffectService,
                subscriptionService,
                categoryService,
                boardAccessPolicy,
                boardVisitService);
        boardApplicationService = new BoardApplicationService(boardService, queryService);

        lenient().when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(anyLong(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(boardCategoryRepository.findByBoard_BoardIdInAndIsActiveOrderByBoard_BoardIdAscSortOrderAsc(any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(boardSubscriptionRepository.countByBoardIds(any()))
                .thenReturn(Collections.emptyList());
        lenient().when(postRepository.countPublicVisiblePostsByBoardIds(any()))
                .thenReturn(Collections.emptyList());
        lenient().when(postRepository.countActiveByBoardIds(any()))
                .thenReturn(Collections.emptyList());
        lenient().when(adminRepository.findByBoard_BoardIdInAndRoleAndIsActiveOrderByBoard_BoardIdAscAdminIdDesc(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(adminRepository.findByUserAndBoard_BoardIdInAndIsActive(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(boardVisitService.getActivitySummaries(any(), any()))
                .thenReturn(Collections.emptyMap());
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
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        board = Board.builder()
                .boardName("Test Board")
                .boardUrl("test-board")
                .creator(user)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 1L);
        ReflectionTestUtils.setField(board, "isActive", true);
        lenient().when(adminRepository.findByUserAndBoardAndIsActive(user, board, true))
                .thenReturn(Optional.of(Admin.builder()
                        .user(user)
                        .board(board)
                        .role("BOARD_ADMIN")
                        .build()));
        lenient().when(adminRepository.existsByUserAndBoardAndIsActive(user, board, true))
                .thenReturn(true);
    }

    @Test
    @DisplayName("활성화된 노드 목록 조회 성공")
    void getActiveBoards_success() {
        // given
        when(boardRepository.findReadableActiveBoardsOrderBySortOrderAscBoardIdAsc(
                null,
                false,
                BoardPolicyConstants.INQUIRY_BOARD_URL))
                .thenReturn(Collections.singletonList(board));

        // when
        List<BoardListResponse> activeBoards = boardService.getActiveBoards(null);

        // then
        assertThat(activeBoards).hasSize(1);
        assertThat(activeBoards.get(0).getBoardName()).isEqualTo("Test Board");
    }

    @Test
    @DisplayName("super admin은 전체 노드 목록을 조회할 수 있다")
    void getAllBoards_superAdmin_success() {
        user.grantSuperAdminRole();
        when(boardRepository.findAllByOrderBySortOrderAscBoardIdAsc()).thenReturn(List.of(board));

        List<AdminBoardResponse> responses = boardService.getAllBoards(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getBoardId()).isEqualTo(1L);
        verify(boardRepository).findAllByOrderBySortOrderAscBoardIdAsc();
    }

    @Test
    @DisplayName("일반 사용자는 전체 노드 목록을 조회할 수 없다")
    void getAllBoards_normalUser_forbidden() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.getAllBoards(1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(boardRepository, never()).findAllByOrderBySortOrderAscBoardIdAsc();
    }

    @Test
    @DisplayName("비인증 요청은 전체 노드 목록을 조회할 수 없다")
    void getAllBoards_nullPrincipal_forbidden() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.getAllBoards(null));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(boardRepository, never()).findAllByOrderBySortOrderAscBoardIdAsc();
    }

    @Test
    @DisplayName("비활성 super admin은 전체 노드 목록을 조회할 수 없다")
    void getAllBoards_inactiveSuperAdmin_forbidden() {
        user.grantSuperAdminRole();
        user.suspend();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.getAllBoards(1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(boardRepository, never()).findAllByOrderBySortOrderAscBoardIdAsc();
    }

    @Test
    @DisplayName("없는 사용자의 전체 노드 목록 조회는 USER_NOT_FOUND를 반환한다")
    void getAllBoards_missingUser_userNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.getAllBoards(99L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        verify(boardRepository, never()).findAllByOrderBySortOrderAscBoardIdAsc();
    }

    @Test
    @DisplayName("노드 관리자는 해당 노드 구독자 후보를 조회할 수 있다")
    void getBoardManagerCandidates_boardAdmin_success() {
        User candidate = User.builder()
                .loginId("candidate")
                .password("password")
                .email("candidate@test.com")
                .displayName("Candidate")
                .build();
        ReflectionTestUtils.setField(candidate, "userId", 2L);
        BoardSubscription subscription = BoardSubscription.builder()
                .user(candidate)
                .board(board)
                .role("MEMBER")
                .sortOrder(1)
                .build();

        when(boardRepository.findByBoardUrl("test-board")).thenReturn(Optional.of(board));
        when(boardSubscriptionRepository.findManagerCandidatesByBoardAndKeyword(eq(board), eq("%cand%"), any()))
                .thenReturn(new PageImpl<>(List.of(subscription), PageRequest.of(0, 10), 1));
        when(adminRepository.findFirstByBoardAndRoleAndIsActiveOrderByAdminIdDesc(board, Role.BOARD_ADMIN, true))
                .thenReturn(Optional.of(Admin.builder()
                        .user(candidate)
                        .board(board)
                        .role(Role.BOARD_ADMIN)
                        .build()));

        Page<BoardManagerCandidateResponse> result = boardService.getBoardManagerCandidates(
                "test-board",
                1L,
                " cand ",
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        BoardManagerCandidateResponse response = result.getContent().get(0);
        assertThat(response.getUserId()).isEqualTo(2L);
        assertThat(response.getLoginId()).isEqualTo("candidate");
        assertThat(response.isCurrentManager()).isTrue();
    }

    @Test
    @DisplayName("board manager candidate keyword escapes LIKE wildcards")
    void getBoardManagerCandidates_escapesLikeWildcards() {
        when(boardRepository.findByBoardUrl("test-board")).thenReturn(Optional.of(board));
        when(boardSubscriptionRepository.findManagerCandidatesByBoardAndKeyword(eq(board), eq("%c!%!_%"), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        when(adminRepository.findFirstByBoardAndRoleAndIsActiveOrderByAdminIdDesc(board, Role.BOARD_ADMIN, true))
                .thenReturn(Optional.empty());

        Page<BoardManagerCandidateResponse> result = boardService.getBoardManagerCandidates(
                "test-board",
                1L,
                " c%_ ",
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isZero();
        verify(boardSubscriptionRepository).findManagerCandidatesByBoardAndKeyword(
                eq(board),
                eq("%c!%!_%"),
                any());
    }

    @Test
    @DisplayName("board manager candidate keyword rejects oversized input")
    void getBoardManagerCandidates_rejectsOversizedKeyword() {
        when(boardRepository.findByBoardUrl("test-board")).thenReturn(Optional.of(board));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.getBoardManagerCandidates(
                        "test-board",
                        1L,
                        "a".repeat(101),
                        PageRequest.of(0, 10)));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(boardSubscriptionRepository, never()).findManagerCandidatesByBoard(any(), any());
        verify(boardSubscriptionRepository, never()).findManagerCandidatesByBoardAndKeyword(any(), any(), any());
    }

    @Test
    @DisplayName("슈퍼 관리자는 노드 관리자 후보를 조회할 수 있다")
    void getBoardManagerCandidates_superAdmin_success() {
        User superUser = User.builder()
                .loginId("super")
                .password("password")
                .email("super@test.com")
                .displayName("Super")
                .build();
        ReflectionTestUtils.setField(superUser, "userId", 3L);
        superUser.grantSuperAdminRole();

        when(boardRepository.findByBoardUrl("test-board")).thenReturn(Optional.of(board));
        when(userRepository.findById(3L)).thenReturn(Optional.of(superUser));
        when(boardSubscriptionRepository.findManagerCandidatesByBoard(eq(board), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        when(adminRepository.findFirstByBoardAndRoleAndIsActiveOrderByAdminIdDesc(board, Role.BOARD_ADMIN, true))
                .thenReturn(Optional.empty());

        Page<BoardManagerCandidateResponse> result = boardService.getBoardManagerCandidates(
                "test-board",
                3L,
                "",
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isZero();
        verify(boardSubscriptionRepository).findManagerCandidatesByBoard(eq(board), any());
    }

    @Test
    @DisplayName("노드 관리자 후보 조회는 null 페이지 요청을 기본값으로 보정한다")
    void getBoardManagerCandidates_normalizesNullPageable() {
        when(boardRepository.findByBoardUrl("test-board")).thenReturn(Optional.of(board));
        when(boardSubscriptionRepository.findManagerCandidatesByBoard(eq(board), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(adminRepository.findFirstByBoardAndRoleAndIsActiveOrderByAdminIdDesc(board, Role.BOARD_ADMIN, true))
                .thenReturn(Optional.empty());

        Page<BoardManagerCandidateResponse> result = boardService.getBoardManagerCandidates(
                "test-board",
                1L,
                null,
                null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(boardSubscriptionRepository).findManagerCandidatesByBoard(eq(board), pageableCaptor.capture());
        assertThat(result.getTotalElements()).isZero();
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageableCaptor.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test
    @DisplayName("노드 관리자 후보 조회는 과대한 페이지 크기와 임의 정렬을 보정한다")
    void getBoardManagerCandidates_capsOversizedPageableAndIgnoresSort() {
        when(boardRepository.findByBoardUrl("test-board")).thenReturn(Optional.of(board));
        when(boardSubscriptionRepository.findManagerCandidatesByBoard(eq(board), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 100), 0));
        when(adminRepository.findFirstByBoardAndRoleAndIsActiveOrderByAdminIdDesc(board, Role.BOARD_ADMIN, true))
                .thenReturn(Optional.empty());

        Page<BoardManagerCandidateResponse> result = boardService.getBoardManagerCandidates(
                "test-board",
                1L,
                null,
                PageRequest.of(2, 500, Sort.by(Sort.Direction.DESC, "loginId")));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(boardSubscriptionRepository).findManagerCandidatesByBoard(eq(board), pageableCaptor.capture());
        assertThat(result.getTotalElements()).isZero();
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageableCaptor.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test
    @DisplayName("노드 관리자 권한이 없으면 후보 조회를 거부한다")
    void getBoardManagerCandidates_forbidden() {
        User otherUser = User.builder()
                .loginId("other")
                .password("password")
                .email("other@test.com")
                .displayName("Other")
                .build();
        ReflectionTestUtils.setField(otherUser, "userId", 99L);

        when(boardRepository.findByBoardUrl("test-board")).thenReturn(Optional.of(board));
        when(userRepository.findById(99L)).thenReturn(Optional.of(otherUser));
        when(adminRepository.existsByUserAndBoardAndIsActive(otherUser, board, true)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.getBoardManagerCandidates("test-board", 99L, null, PageRequest.of(0, 10)));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(boardSubscriptionRepository, never()).findManagerCandidatesByBoard(any(), any());
        verify(boardSubscriptionRepository, never()).findManagerCandidatesByBoardAndKeyword(any(), any(), any());
    }

    @Test
    @DisplayName("노드 구독 성공")
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
    @DisplayName("쓰기 불가 사용자는 노드을 구독할 수 없다")
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
    @DisplayName("이미 구독한 노드은 다시 구독할 수 없다")
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
        verify(boardSubscriptionRepository, never()).saveAndFlush(any(BoardSubscription.class));
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
    }

    @Test
    @DisplayName("노드 관리자 이관은 잠금 조회한 노드으로 권한 검증 후 위임한다")
    void transferBoardManager_usesLockedBoardLookup() {
        User nextManager = User.builder()
                .loginId("nextmanager")
                .password("password")
                .email("next@test.com")
                .displayName("Next Manager")
                .build();
        ReflectionTestUtils.setField(nextManager, "userId", 2L);

        when(boardRepository.findByBoardUrlForUpdate("test-board")).thenReturn(Optional.of(board));
        when(adminEligibleUserService.getActiveUserByLoginId("nextmanager")).thenReturn(nextManager);
        when(boardSubscriptionRepository.existsByUserAndBoard(nextManager, board)).thenReturn(true);

        BoardCommandResult result = boardService.transferBoardManager("test-board", "nextmanager", 1L);

        assertThat(result.boardUrl()).isEqualTo("test-board");
        verify(boardRepository).findByBoardUrlForUpdate("test-board");
        verify(boardRepository, never()).findByBoardUrl("test-board");
        verify(boardManagerAssignmentService).assignBoardManager(board, nextManager);
    }

    @Test
    @DisplayName("노드 관리자 이관 상세 응답은 이관 후 상세 조회 결과를 반환한다")
    void transferBoardManagerDetail_returnsDetailAfterTransfer() {
        User nextManager = User.builder()
                .loginId("nextmanager")
                .password("password")
                .email("next@test.com")
                .displayName("Next Manager")
                .build();
        ReflectionTestUtils.setField(nextManager, "userId", 2L);

        when(boardRepository.findByBoardUrlForUpdate("test-board")).thenReturn(Optional.of(board));
        when(adminEligibleUserService.getActiveUserByLoginId("nextmanager")).thenReturn(nextManager);
        when(boardSubscriptionRepository.existsByUserAndBoard(nextManager, board)).thenReturn(true);
        when(boardRepository.findByBoardUrl("test-board")).thenReturn(Optional.of(board));

        BoardDetailResponse response = boardApplicationService.transferBoardManagerDetail("test-board", "nextmanager", 1L);

        assertThat(response.getBoardUrl()).isEqualTo("test-board");
        verify(boardManagerAssignmentService).assignBoardManager(board, nextManager);
        InOrder inOrder = inOrder(boardRepository);
        inOrder.verify(boardRepository).findByBoardUrlForUpdate("test-board");
        inOrder.verify(boardRepository).findByBoardUrl("test-board");
    }

    @Test
    @DisplayName("노드 관리자 이관 대상이 구독자가 아니면 FORBIDDEN을 반환한다")
    void transferBoardManager_rejectsNonSubscriber() {
        User nextManager = User.builder()
                .loginId("nextmanager")
                .password("password")
                .email("next@test.com")
                .displayName("Next Manager")
                .build();
        ReflectionTestUtils.setField(nextManager, "userId", 2L);

        when(boardRepository.findByBoardUrlForUpdate("test-board")).thenReturn(Optional.of(board));
        when(adminEligibleUserService.getActiveUserByLoginId("nextmanager")).thenReturn(nextManager);
        when(boardSubscriptionRepository.existsByUserAndBoard(nextManager, board)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.transferBoardManager("test-board", "nextmanager", 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(boardManagerAssignmentService, never()).assignBoardManager(any(), any());
    }

    @Test
    @DisplayName("노드 관리자 이관은 권한이 없으면 대상 사용자 조회와 배정을 하지 않는다")
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
        when(adminRepository.existsByUserAndBoardAndIsActive(otherUser, board, true)).thenReturn(false);

        BusinessException exception;
        exception = assertThrows(BusinessException.class,
                () -> boardService.transferBoardManager("test-board", "nextmanager", 99L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(boardRepository).findByBoardUrlForUpdate("test-board");
        verify(boardRepository, never()).findByBoardUrl("test-board");
        verify(adminEligibleUserService, never()).getActiveUserByLoginId(anyString());
        verify(boardManagerAssignmentService, never()).assignBoardManager(any(), any());
    }

    @Test
    @DisplayName("노드 관리자 이관은 잠금 조회에서 노드이 없으면 BOARD_NOT_FOUND를 던진다")
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
    @DisplayName("노드 생성 성공")
    void createBoard_success() {
        // given
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(globalConfigService.getConfig(anyString())).thenReturn("500");
        when(boardRepository.saveAndFlush(any(Board.class))).thenReturn(board);
        when(boardCategoryRepository.save(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);

        // when
        BoardCommandResult result = boardService.createBoard(creatorId, request);

        // then
        assertThat(result.boardUrl()).isEqualTo("test-board");
        InOrder inOrder = inOrder(boardRepository, pointService, boardCategoryRepository, boardManagerAssignmentService);
        inOrder.verify(boardRepository).saveAndFlush(any(Board.class));
        inOrder.verify(pointService).spendPoint(
                eq(creatorId),
                eq(500),
                eq("스페이스 생성 (Test Board)"),
                eq(1L),
                eq("BOARD_CREATE"));
        ArgumentCaptor<BoardCategory> categoryCaptor = ArgumentCaptor.forClass(BoardCategory.class);
        inOrder.verify(boardCategoryRepository).save(categoryCaptor.capture());
        inOrder.verify(boardManagerAssignmentService).assignBoardManager(board, user);
        assertThat(categoryCaptor.getValue().getName()).isEqualTo("일반");
        assertThat(categoryCaptor.getValue().isDefaultCategory()).isTrue();
    }

    @Test
    @DisplayName("노드 생성 상세 응답은 저장 후 상세 조회 결과를 반환한다")
    void createBoard_normalizesBoardUrlBeforeSave() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", " trimmed-board ", null, null, true);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(globalConfigService.getConfig("POINT_BOARD_CREATE_COST")).thenReturn("0");
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        when(boardRepository.saveAndFlush(any(Board.class))).thenAnswer(invocation -> {
            Board savedBoard = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedBoard, "boardId", 3L);
            return savedBoard;
        });
        when(boardCategoryRepository.save(any(BoardCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BoardCommandResult result = boardService.createBoard(creatorId, request);

        assertThat(result.boardUrl()).isEqualTo("trimmed-board");
        verify(boardRepository).saveAndFlush(argThat(savedBoard ->
                "trimmed-board".equals(savedBoard.getBoardUrl())));
    }

    @Test
    @DisplayName("?몃뱶 ?앹꽦 ?곸꽭 ?묐떟? ??????곸꽭 議고쉶 寃곌낵瑜?諛섑솚?쒕떎")
    void createBoardDetail_returnsDetailAfterCreate() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(globalConfigService.getConfig(anyString())).thenReturn("500");
        when(boardRepository.saveAndFlush(any(Board.class))).thenReturn(board);
        when(boardCategoryRepository.save(any(BoardCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        when(boardRepository.findByBoardUrl("test-board")).thenReturn(Optional.of(board));

        BoardDetailResponse response = boardApplicationService.createBoardDetail(creatorId, request);

        assertThat(response.getBoardUrl()).isEqualTo("test-board");
        InOrder inOrder = inOrder(boardRepository);
        inOrder.verify(boardRepository).saveAndFlush(any(Board.class));
        inOrder.verify(boardRepository).findByBoardUrl("test-board");
    }

    @Test
    @DisplayName("노드 생성 비용 설정이 잘못되면 기본값으로 차감한다")
    void createBoard_invalidCostConfig_usesDefaultCost() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(globalConfigService.getConfig("POINT_BOARD_CREATE_COST")).thenReturn("invalid");
        when(boardRepository.saveAndFlush(any(Board.class))).thenReturn(board);
        when(boardCategoryRepository.save(any(BoardCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);

        boardService.createBoard(creatorId, request);

        verify(pointService).spendPoint(
                eq(creatorId),
                eq(500),
                eq("스페이스 생성 (Test Board)"),
                eq(1L),
                eq("BOARD_CREATE"));
    }

    @Test
    @DisplayName("노드 생성 비용 설정이 0이면 포인트를 차감하지 않는다")
    void createBoard_zeroCostConfig_skipsPointSpend() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
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
    @DisplayName("노드 생성 시 파일 기반 아이콘이면 영구 연관한다")
    void createBoard_withUploadedIcon_associatesBoardIcon() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest(
                "New Board",
                "new-board",
                "New Description",
                " /api/v1/files/55 ",
                null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
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
        verify(boardRepository).saveAndFlush(argThat(savedBoard ->
                "/api/v1/files/55".equals(savedBoard.getIconUrl())));
    }

    @Test
    @DisplayName("노드 생성 전 작성자의 활성 상태를 검증한다")
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
    @DisplayName("AI 사용 노드 생성 시 BoardAiInfo를 생성한다")
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
        when(globalConfigService.getConfig(anyString())).thenReturn("500");
        when(boardRepository.saveAndFlush(any(Board.class))).thenAnswer(invocation -> {
            Board savedBoard = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedBoard, "boardId", 3L);
            return savedBoard;
        });
        when(boardCategoryRepository.save(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);

        BoardCommandResult result = boardService.createBoard(creatorId, request);

        assertThat(result.boardUrl()).isEqualTo("private-ai");
        verify(boardRepository).saveAndFlush(argThat(savedBoard ->
                Boolean.FALSE.equals(savedBoard.getIsPublic()) && !savedBoard.isAgentEnabled()));
        verify(boardAiInfoRepository, never()).save(any(BoardAiInfo.class));
    }

    @Test
    @DisplayName("노드 수정 상세 응답은 변경된 URL로 상세 조회한다")
    void updateBoardDetail_returnsDetailForUpdatedUrl() {
        BoardUpdateRequest request = createBoardUpdateRequest("Updated Board", "updated-board", null);

        when(boardRepository.findByBoardUrlForUpdate("test-board")).thenReturn(Optional.of(board));
        when(boardRepository.saveAndFlush(board)).thenReturn(board);
        when(boardRepository.findByBoardUrl("updated-board")).thenReturn(Optional.of(board));

        BoardDetailResponse response = boardApplicationService.updateBoardDetail("test-board", request, 1L);

        assertThat(response.getBoardUrl()).isEqualTo("updated-board");
        InOrder inOrder = inOrder(boardRepository);
        inOrder.verify(boardRepository).findByBoardUrlForUpdate("test-board");
        inOrder.verify(boardRepository).saveAndFlush(board);
        inOrder.verify(boardRepository).findByBoardUrl("updated-board");
    }

    @Test
    @DisplayName("Private board update disables agent use")
    void updateBoard_normalizesPathAndRequestedBoardUrl() {
        BoardUpdateRequest request = createBoardUpdateRequest("Updated Board", " updated-board ", null);

        when(boardRepository.findByBoardUrlForUpdate("test-board")).thenReturn(Optional.of(board));
        when(boardRepository.saveAndFlush(board)).thenReturn(board);

        BoardCommandResult result = boardService.updateBoard(" test-board ", request, 1L);

        assertThat(result.boardUrl()).isEqualTo("updated-board");
        assertThat(board.getBoardUrl()).isEqualTo("updated-board");
        verify(boardRepository).findByBoardUrlForUpdate("test-board");
    }

    @Test
    @DisplayName("Private board update disables agent use")
    void updateBoard_privateBoardDisablesAgentUse() {
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

        when(boardRepository.findByBoardUrlForUpdate("test-board")).thenReturn(Optional.of(board));

        BoardCommandResult result = boardService.updateBoard("test-board", request, 1L);

        assertThat(result.boardUrl()).isEqualTo("test-board");
        assertThat(board.getIsPublic()).isFalse();
        assertThat(board.isAgentEnabled()).isFalse();
        verify(boardRepository).findByBoardUrlForUpdate("test-board");
        verify(boardRepository, never()).findByBoardUrl("test-board");
        verify(boardAiInfoRepository, never()).save(any(BoardAiInfo.class));
        verify(semanticSearchEventPublisher).publishBoardContentReindex(1L);
    }

    @Test
    void updateBoard_inactiveBoardPublishesSemanticReindex() {
        BoardUpdateRequest request = new BoardUpdateRequest();
        ReflectionTestUtils.setField(request, "boardName", "Inactive Board");
        ReflectionTestUtils.setField(request, "description", "Updated Description");
        ReflectionTestUtils.setField(request, "boardUrl", "test-board");
        ReflectionTestUtils.setField(request, "iconUrl", null);
        ReflectionTestUtils.setField(request, "sortOrder", 1);
        ReflectionTestUtils.setField(request, "isActive", false);
        ReflectionTestUtils.setField(request, "isPublic", true);
        ReflectionTestUtils.setField(request, "moderationReason", "maintenance");

        when(boardRepository.findByBoardUrlForUpdate("test-board")).thenReturn(Optional.of(board));

        boardService.updateBoard("test-board", request, 1L);

        assertThat(board.getIsActive()).isFalse();
        verify(semanticSearchEventPublisher).publishBoardContentReindex(1L);
    }

    @Test
    @DisplayName("노드 수정 시 sortOrder를 생략하면 기존 정렬값을 유지한다")
    void updateBoard_omittedSortOrderKeepsExistingValue() {
        BoardUpdateRequest request = new BoardUpdateRequest();
        ReflectionTestUtils.setField(request, "boardName", "Updated Board");
        ReflectionTestUtils.setField(request, "description", "Updated Description");
        ReflectionTestUtils.setField(request, "boardUrl", "test-board");
        ReflectionTestUtils.setField(request, "iconUrl", null);
        ReflectionTestUtils.setField(request, "isActive", true);
        ReflectionTestUtils.setField(request, "isPublic", true);
        ReflectionTestUtils.setField(board, "sortOrder", 7);

        when(boardRepository.findByBoardUrlForUpdate("test-board")).thenReturn(Optional.of(board));

        BoardCommandResult result = boardService.updateBoard("test-board", request, 1L);

        assertThat(result.boardUrl()).isEqualTo("test-board");
        assertThat(board.getSortOrder()).isEqualTo(7);
    }

    @Test
    @DisplayName("Board update applies uploaded icon replacement")
    void updateBoard_replacesBoardIcon() {
        BoardUpdateRequest request = new BoardUpdateRequest();
        ReflectionTestUtils.setField(request, "boardName", "Updated Board");
        ReflectionTestUtils.setField(request, "description", "Updated Description");
        ReflectionTestUtils.setField(request, "boardUrl", "test-board");
        ReflectionTestUtils.setField(request, "iconUrl", "/api/v1/files/88");
        ReflectionTestUtils.setField(request, "sortOrder", 1);
        ReflectionTestUtils.setField(request, "isActive", true);
        ReflectionTestUtils.setField(request, "isPublic", true);

        ReflectionTestUtils.setField(board, "iconUrl", "/api/v1/files/77");

        when(boardRepository.findByBoardUrlForUpdate("test-board")).thenReturn(Optional.of(board));

        BoardCommandResult result = boardService.updateBoard("test-board", request, 1L);

        assertThat(result.boardUrl()).isEqualTo("test-board");
        assertThat(board.getIconUrl()).isEqualTo("/api/v1/files/88");
        verify(fileService).replaceBoardIcon(88L, 1L, 1L);
        verify(fileService).deleteFileWithStorageIfAssociated(77L, 1L, FileService.RELATED_TYPE_BOARD_ICON);
    }

    @Test
    @DisplayName("Board update skips icon reassociation when icon URL is unchanged")
    void updateBoard_sameBoardIconSkipsReplacement() {
        BoardUpdateRequest request = new BoardUpdateRequest();
        ReflectionTestUtils.setField(request, "boardName", "Updated Board");
        ReflectionTestUtils.setField(request, "description", "Updated Description");
        ReflectionTestUtils.setField(request, "boardUrl", "test-board");
        ReflectionTestUtils.setField(request, "iconUrl", "/api/v1/files/77");
        ReflectionTestUtils.setField(request, "sortOrder", 1);
        ReflectionTestUtils.setField(request, "isActive", true);
        ReflectionTestUtils.setField(request, "isPublic", true);
        ReflectionTestUtils.setField(request, "agentUseYn", true);

        ReflectionTestUtils.setField(board, "iconUrl", "/api/v1/files/77");
        ReflectionTestUtils.setField(board, "agentUseYn", false);

        when(boardRepository.findByBoardUrlForUpdate("test-board")).thenReturn(Optional.of(board));

        BoardCommandResult result = boardService.updateBoard("test-board", request, 1L);

        assertThat(result.boardUrl()).isEqualTo("test-board");
        assertThat(board.isAgentEnabled()).isTrue();
        verify(fileService, never()).replaceBoardIcon(anyLong(), anyLong(), anyLong());
        verify(fileService, never()).deleteFileWithStorageIfAssociated(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("노드 수정 실패 - 저장 시 board_name 충돌이면 DUPLICATE_BOARD_NAME")
    void updateBoard_duplicateBoardNameDuringFlush() {
        BoardUpdateRequest request = createBoardUpdateRequest("Updated Board", "test-board", "/api/v1/files/88");

        when(boardRepository.findByBoardUrlForUpdate("test-board")).thenReturn(Optional.of(board));
        when(boardRepository.saveAndFlush(board))
                .thenThrow(new DataIntegrityViolationException("duplicate key board_name"));

        BusinessException exception;
        exception = assertThrows(BusinessException.class,
                    () -> boardService.updateBoard("test-board", request, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_BOARD_NAME);
        verify(fileService, never()).replaceBoardIcon(anyLong(), anyLong(), anyLong());
        verify(fileService, never()).deleteFileWithStorageIfAssociated(anyLong(), anyLong(), anyString());
        verify(boardAiInfoRepository, never()).save(any(BoardAiInfo.class));
    }

    @Test
    @DisplayName("노드 수정 실패 - 저장 시 board_url 충돌이면 DUPLICATE_BOARD_URL")
    void updateBoard_duplicateBoardUrlDuringFlush() {
        BoardUpdateRequest request = createBoardUpdateRequest("Test Board", "updated-board", "/api/v1/files/88");

        when(boardRepository.findByBoardUrlForUpdate("test-board")).thenReturn(Optional.of(board));
        when(boardRepository.saveAndFlush(board))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key",
                        new ConstraintViolationException("duplicate", new java.sql.SQLException("duplicate"),
                                "uk_boards_board_url")));

        BusinessException exception;
        exception = assertThrows(BusinessException.class,
                    () -> boardService.updateBoard("test-board", request, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_BOARD_URL);
        verify(fileService, never()).replaceBoardIcon(anyLong(), anyLong(), anyLong());
        verify(fileService, never()).deleteFileWithStorageIfAssociated(anyLong(), anyLong(), anyString());
        verify(boardAiInfoRepository, never()).save(any(BoardAiInfo.class));
    }

    @Test
    @DisplayName("노드 수정 실패 - 제약 메시지를 식별할 수 없으면 DUPLICATE_RESOURCE")
    void updateBoard_duplicateFallbackDuringFlush() {
        BoardUpdateRequest request = createBoardUpdateRequest("Updated Board", "test-board", "/api/v1/files/88");

        when(boardRepository.findByBoardUrlForUpdate("test-board")).thenReturn(Optional.of(board));
        when(boardRepository.saveAndFlush(board))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        BusinessException exception;
        exception = assertThrows(BusinessException.class,
                    () -> boardService.updateBoard("test-board", request, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
        verify(fileService, never()).replaceBoardIcon(anyLong(), anyLong(), anyLong());
        verify(fileService, never()).deleteFileWithStorageIfAssociated(anyLong(), anyLong(), anyString());
        verify(boardAiInfoRepository, never()).save(any(BoardAiInfo.class));
    }

    @Test
    @DisplayName("노드 삭제는 잠금 조회한 노드을 비활성화한다")
    void deleteBoard_usesLockedBoardLookup() {
        when(boardRepository.findByBoardUrlForUpdate("test-board")).thenReturn(Optional.of(board));

        boardService.deleteBoard("test-board", 1L);

        assertThat(board.getIsActive()).isFalse();
        verify(boardRepository).findByBoardUrlForUpdate("test-board");
        verify(boardRepository, never()).findByBoardUrl("test-board");
    }

    @Test
    @DisplayName("카테고리 생성은 이름을 trim하고 활성 이름 중복을 사전 검증한다")
    void createCategory_trimsNameAndChecksDuplicateActiveName() {
        CategoryRequest request = categoryRequest("  General  ", 1, "USER");
        when(boardRepository.findByBoardUrlForUpdate("test-board")).thenReturn(Optional.of(board));
        when(boardCategoryRepository.existsByBoard_BoardIdAndNameAndIsActive(1L, "General", true))
                .thenReturn(false);
        when(boardCategoryRepository.saveAndFlush(any(BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boardService.createCategory("test-board", request, 1L);

        ArgumentCaptor<BoardCategory> categoryCaptor = ArgumentCaptor.forClass(BoardCategory.class);
        verify(boardRepository).findByBoardUrlForUpdate("test-board");
        verify(boardCategoryRepository).saveAndFlush(categoryCaptor.capture());
        assertThat(categoryCaptor.getValue().getName()).isEqualTo("General");
    }

    @Test
    @DisplayName("기본 카테고리 생성 요청은 기존 기본 카테고리를 해제하고 새 기본으로 저장한다")
    void createCategory_defaultRequest_clearsExistingDefault() {
        CategoryRequest request = categoryRequest("Default", 2, "BOARD_ADMIN", true);
        BoardCategory previousDefault = BoardCategory.builder()
                .board(board)
                .name("Old Default")
                .sortOrder(1)
                .isDefault(true)
                .build();
        ReflectionTestUtils.setField(previousDefault, "categoryId", 9L);

        when(boardRepository.findByBoardUrlForUpdate("test-board")).thenReturn(Optional.of(board));
        when(boardCategoryRepository.existsByBoard_BoardIdAndNameAndIsActive(1L, "Default", true))
                .thenReturn(false);
        when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(1L, true))
                .thenReturn(List.of(previousDefault));
        when(boardCategoryRepository.saveAndFlush(any(BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response;
        response = boardService.createCategory("test-board", request, 1L);

        ArgumentCaptor<BoardCategory> categoryCaptor = ArgumentCaptor.forClass(BoardCategory.class);
        verify(boardRepository).findByBoardUrlForUpdate("test-board");
        verify(boardCategoryRepository).saveAndFlush(categoryCaptor.capture());
        assertThat(previousDefault.isDefaultCategory()).isFalse();
        assertThat(categoryCaptor.getValue().isDefaultCategory()).isTrue();
        assertThat(response.isDefault()).isTrue();
    }

    @Test
    @DisplayName("카테고리 생성은 같은 노드의 활성 이름 중복을 거부한다")
    void createCategory_duplicateActiveName_throwsDuplicateResource() {
        CategoryRequest request = categoryRequest("General", 1, "USER");
        when(boardRepository.findByBoardUrlForUpdate("test-board")).thenReturn(Optional.of(board));
        when(boardCategoryRepository.existsByBoard_BoardIdAndNameAndIsActive(1L, "General", true))
                .thenReturn(true);

        BusinessException exception;
        exception = assertThrows(BusinessException.class,
                    () -> boardService.createCategory("test-board", request, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
        verify(boardCategoryRepository, never()).saveAndFlush(any(BoardCategory.class));
    }

    @Test
    @DisplayName("카테고리 생성 저장 중 활성 이름 제약 충돌은 중복 리소스로 매핑한다")
    void createCategory_activeNameConstraintOnSave_throwsDuplicateResource() {
        CategoryRequest request = categoryRequest("General", 1, "USER");
        when(boardRepository.findByBoardUrlForUpdate("test-board")).thenReturn(Optional.of(board));
        when(boardCategoryRepository.existsByBoard_BoardIdAndNameAndIsActive(1L, "General", true))
                .thenReturn(false);
        when(boardCategoryRepository.saveAndFlush(any(BoardCategory.class)))
                .thenThrow(activeCategoryConstraintViolation("uq_board_categories_active_name"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.createCategory("test-board", request, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
        assertThat(exception.getMessageKey()).isEqualTo("error.board.category.duplicateActive");
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
        stubCategoryBoardLock(10L);
        when(boardCategoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(boardCategoryRepository.existsByBoard_BoardIdAndNameAndIsActiveAndCategoryIdNot(
                1L,
                "General",
                true,
                10L))
                .thenReturn(true);

        BusinessException exception;
        exception = assertThrows(BusinessException.class,
                    () -> boardService.updateCategory(10L, request, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
        verify(boardCategoryRepository, never()).saveAndFlush(any(BoardCategory.class));
    }

    @Test
    @DisplayName("카테고리 수정 저장 중 활성 이름 제약 충돌은 중복 리소스로 매핑한다")
    void updateCategory_activeNameConstraintOnSave_throwsDuplicateResource() {
        CategoryRequest request = categoryRequest("General", 1, "USER");
        BoardCategory category = BoardCategory.builder()
                .board(board)
                .name("News")
                .sortOrder(1)
                .minWriteRole("USER")
                .build();
        ReflectionTestUtils.setField(category, "categoryId", 10L);
        stubCategoryBoardLock(10L);
        when(boardCategoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(boardCategoryRepository.existsByBoard_BoardIdAndNameAndIsActiveAndCategoryIdNot(
                1L,
                "General",
                true,
                10L))
                .thenReturn(false);
        when(boardCategoryRepository.saveAndFlush(category))
                .thenThrow(activeCategoryConstraintViolation("uk_board_categories_board_name_active"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.updateCategory(10L, request, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
        assertThat(exception.getMessageKey()).isEqualTo("error.board.category.duplicateActive");
    }

    @Test
    @DisplayName("category update rejects null sortOrder before persistence")
    void updateCategory_nullSortOrder_throwsValidationError() {
        CategoryRequest request = categoryRequest("General", null, "USER");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.updateCategory(10L, request, null));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(boardCategoryRepository, never()).findBoardIdByCategoryId(anyLong());
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
        stubCategoryBoardLock(10L);
        when(boardCategoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(boardCategoryRepository.saveAndFlush(category)).thenReturn(category);

        boardService.updateCategory(10L, request, 1L);

        verify(boardCategoryRepository, never()).existsByBoard_BoardIdAndNameAndIsActiveAndCategoryIdNot(
                anyLong(),
                anyString(),
                anyBoolean(),
                anyLong());
        verify(boardCategoryRepository).saveAndFlush(category);
    }

    @Test
    @DisplayName("비활성 카테고리는 기본 카테고리로 지정할 수 없다")
    void updateCategory_inactiveCategoryDefaultRequest_throwsValidationError() {
        CategoryRequest request = categoryRequest("Archived", 1, "USER", true);
        BoardCategory category = BoardCategory.builder()
                .board(board)
                .name("Archived")
                .sortOrder(1)
                .minWriteRole("USER")
                .build();
        ReflectionTestUtils.setField(category, "categoryId", 10L);
        category.deactivate();
        stubCategoryBoardLock(10L);
        when(boardCategoryRepository.findById(10L)).thenReturn(Optional.of(category));

        BusinessException exception;
        exception = assertThrows(BusinessException.class,
                    () -> boardService.updateCategory(10L, request, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(boardCategoryRepository, never()).findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(anyLong(), any());
        verify(boardCategoryRepository, never()).saveAndFlush(any(BoardCategory.class));
    }

    @Test
    @DisplayName("카테고리 수정으로 기본 카테고리를 교체할 수 있다")
    void updateCategory_defaultRequest_switchesDefaultCategory() {
        CategoryRequest request = categoryRequest("New Default", 2, "BOARD_ADMIN", true);
        BoardCategory previousDefault = BoardCategory.builder()
                .board(board)
                .name("Old Default")
                .sortOrder(1)
                .isDefault(true)
                .build();
        ReflectionTestUtils.setField(previousDefault, "categoryId", 9L);
        BoardCategory category = BoardCategory.builder()
                .board(board)
                .name("General")
                .sortOrder(2)
                .minWriteRole("USER")
                .build();
        ReflectionTestUtils.setField(category, "categoryId", 10L);

        stubCategoryBoardLock(10L);
        when(boardCategoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(boardCategoryRepository.existsByBoard_BoardIdAndNameAndIsActiveAndCategoryIdNot(
                1L, "New Default", true, 10L))
                .thenReturn(false);
        when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(1L, true))
                .thenReturn(List.of(previousDefault, category));
        when(boardCategoryRepository.saveAndFlush(category)).thenReturn(category);

        CategoryResponse response;
        response = boardService.updateCategory(10L, request, 1L);

        InOrder lockOrder = inOrder(boardCategoryRepository, boardRepository);
        lockOrder.verify(boardCategoryRepository).findBoardIdByCategoryId(10L);
        lockOrder.verify(boardRepository).findByIdForUpdate(1L);
        lockOrder.verify(boardCategoryRepository).findById(10L);
        assertThat(previousDefault.isDefaultCategory()).isFalse();
        assertThat(category.isDefaultCategory()).isTrue();
        assertThat(category.getMinWriteRole()).isEqualTo("BOARD_ADMIN");
        assertThat(response.isDefault()).isTrue();
    }

    @Test
    @DisplayName("기본 카테고리는 직접 기본 해제할 수 없다")
    void updateCategory_unsetDefault_throwsValidationError() {
        CategoryRequest request = categoryRequest("Default", 1, "USER", false);
        BoardCategory category = BoardCategory.builder()
                .board(board)
                .name("Default")
                .sortOrder(1)
                .isDefault(true)
                .build();
        ReflectionTestUtils.setField(category, "categoryId", 10L);

        stubCategoryBoardLock(10L);
        when(boardCategoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(boardCategoryRepository.existsByBoard_BoardIdAndNameAndIsActiveAndCategoryIdNot(
                1L, "Default", true, 10L))
                .thenReturn(false);

        BusinessException exception;
        exception = assertThrows(BusinessException.class,
                    () -> boardService.updateCategory(10L, request, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(boardCategoryRepository, never()).saveAndFlush(any(BoardCategory.class));
    }

    @Test
    @DisplayName("기본 카테고리는 삭제할 수 없다")
    void deleteCategory_defaultCategory_throwsValidationError() {
        BoardCategory category = BoardCategory.builder()
                .board(board)
                .name("Default")
                .sortOrder(1)
                .isDefault(true)
                .build();
        ReflectionTestUtils.setField(category, "categoryId", 10L);
        stubCategoryBoardLock(10L);
        when(boardCategoryRepository.findById(10L)).thenReturn(Optional.of(category));

        BusinessException exception;
        exception = assertThrows(BusinessException.class,
                    () -> boardService.deleteCategory(10L, 1L));

        InOrder lockOrder = inOrder(boardCategoryRepository, boardRepository);
        lockOrder.verify(boardCategoryRepository).findBoardIdByCategoryId(10L);
        lockOrder.verify(boardRepository).findByIdForUpdate(1L);
        lockOrder.verify(boardCategoryRepository).findById(10L);
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(category.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("노드 생성 포인트 부족 예외는 PointService 경로로 유지된다")
    void createBoard_insufficientPoints_propagatesFromPointService() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
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
    @DisplayName("노드 생성 실패 - 저장 시 board_name 충돌이면 DUPLICATE_BOARD_NAME")
    void createBoard_duplicateBoardNameDuringFlush() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        when(boardRepository.saveAndFlush(any(Board.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key board_name"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.createBoard(creatorId, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_BOARD_NAME);
        verify(pointService, never()).spendPoint(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("노드 생성 실패 - 저장 시 board_url 충돌이면 DUPLICATE_BOARD_URL")
    void createBoard_duplicateBoardUrlDuringFlush() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        when(boardRepository.saveAndFlush(any(Board.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key board_url"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.createBoard(creatorId, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_BOARD_URL);
        verify(pointService, never()).spendPoint(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("노드 생성 실패 - 제약 메시지를 식별할 수 없으면 DUPLICATE_RESOURCE")
    void createBoard_duplicateFallbackDuringFlush() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        when(boardRepository.saveAndFlush(any(Board.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.createBoard(creatorId, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("노드 생성 실패 - 중첩 ConstraintViolationException의 제약명으로 board_url 충돌을 판별한다")
    void createBoard_duplicateBoardUrlByConstraintName() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
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
    @DisplayName("노드 생성 실패 - 중첩 ConstraintViolationException의 제약명으로 board_name 충돌을 판별한다")
    void createBoard_duplicateBoardNameByConstraintName() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
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
    @DisplayName("노드 생성 실패 - legacy 제약명으로도 board_url 충돌을 판별한다")
    void createBoard_duplicateBoardUrlByLegacyConstraintName() {
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null, null);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
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
    @DisplayName("인기 노드 목록 조회 성공")
    void getTopBoards_success() {
        // given
        when(boardRepository.findTopPublicBoardPostCounts(anyString(), any()))
                .thenReturn(List.of(topBoardPostCount(1L, 37L)));
        when(boardRepository.findByBoardIdIn(List.of(1L))).thenReturn(Collections.singletonList(board));

        // when
        List<BoardListResponse> boards = boardService.getTopBoards(null);

        // then
        assertThat(boards).hasSize(1);
        assertThat(boards.get(0).getPostCount()).isEqualTo(37L);
        verify(boardRepository).findTopPublicBoardPostCounts(anyString(), any());
        verify(boardRepository).findByBoardIdIn(List.of(1L));
        verify(postRepository, never()).countPublicVisiblePostsByBoardIds(any());
        verify(postRepository, never()).countActiveByBoardIds(any());
    }

    @Test
    @DisplayName("일반 로그인 사용자는 공개 인기 노드 전용 쿼리를 사용한다")
    void getTopBoards_authenticatedUsesPublicQueryWhenUserHasNoElevatedAccess() {
        when(adminRepository.existsByUserAndIsActive(user, true)).thenReturn(false);
        when(boardRepository.findTopPublicBoardPostCounts(anyString(), any()))
                .thenReturn(List.of(topBoardPostCount(1L, 11L)));
        when(boardRepository.findByBoardIdIn(List.of(1L))).thenReturn(Collections.singletonList(board));

        List<BoardListResponse> boards = boardService.getTopBoards(1L);

        assertThat(boards).extracting(BoardListResponse::getBoardUrl).containsExactly("test-board");
        verify(adminRepository).existsByUserAndIsActive(user, true);
        verify(boardRepository).findTopPublicBoardPostCounts(anyString(), any());
        verify(boardRepository).findByBoardIdIn(List.of(1L));
        verify(boardRepository, never()).findTopBoardIdsByPostCount(any());
        verify(boardRepository, never()).findTopReadableBoardPostCounts(any(), anyBoolean(), anyString(), any());
    }

    @Test
    @DisplayName("userId 기반 인기 노드 조회는 principal 해석 없이 사용자 ID로 조회한다")
    void getTopBoards_userIdUsesPublicQueryWhenUserHasNoElevatedAccess() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRepository.existsByUserAndIsActive(user, true)).thenReturn(false);
        when(boardRepository.findTopPublicBoardPostCounts(anyString(), any()))
                .thenReturn(List.of(topBoardPostCount(1L, 11L)));
        when(boardRepository.findByBoardIdIn(List.of(1L))).thenReturn(Collections.singletonList(board));

        List<BoardListResponse> boards = boardService.getTopBoardsByUserId(1L);

        assertThat(boards).extracting(BoardListResponse::getBoardUrl).containsExactly("test-board");
        verify(userRepository).findById(1L);
        verify(boardRepository).findTopPublicBoardPostCounts(anyString(), any());
        verify(boardRepository, never()).findTopReadableBoardPostCounts(any(), anyBoolean(), anyString(), any());
    }

    @Test
    @DisplayName("userId 기반 인기 노드 조회는 요청 limit을 공개 노드 쿼리에 전달한다")
    void getTopBoardsByUserId_usesRequestedLimitForPublicQuery() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRepository.existsByUserAndIsActive(user, true)).thenReturn(false);
        when(boardRepository.findTopPublicBoardPostCounts(anyString(), any())).thenReturn(List.of());

        List<BoardListResponse> boards = boardService.getTopBoardsByUserId(1L, 6);

        assertThat(boards).isEmpty();
        verify(boardRepository).findTopPublicBoardPostCounts(anyString(), eq(PageRequest.of(0, 6)));
        verify(boardRepository, never()).findTopReadableBoardPostCounts(any(), anyBoolean(), anyString(), any());
    }

    @Test
    @DisplayName("userId 기반 인기 노드 조회는 없는 사용자면 USER_NOT_FOUND를 반환한다")
    void getTopBoards_userIdNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.getTopBoardsByUserId(99L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        verify(boardRepository, never()).findTopPublicBoardPostCounts(anyString(), any());
        verify(boardRepository, never()).findTopReadableBoardPostCounts(any(), anyBoolean(), anyString(), any());
    }

    @Test
    @DisplayName("userId 기반 인기 노드 조회는 권한 사용자의 readable 노드 쿼리를 사용한다")
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
        when(boardRepository.findTopReadableBoardPostCounts(eq(user), eq(false), anyString(), any()))
                .thenReturn(List.of(topBoardPostCount(2L, 5L)));
        when(boardRepository.findByBoardIdIn(List.of(2L))).thenReturn(List.of(privateBoard));

        List<BoardListResponse> boards = boardService.getTopBoardsByUserId(1L);

        assertThat(boards).extracting(BoardListResponse::getBoardUrl).containsExactly("private-board");
        assertThat(boards).extracting(BoardListResponse::getPostCount).containsExactly(5L);
        verify(userRepository).findById(1L);
        verify(boardRepository).findTopReadableBoardPostCounts(eq(user), eq(false), anyString(), any());
        verify(boardRepository, never()).findTopPublicBoardPostCounts(anyString(), any());
        verify(postRepository, never()).countPublicVisiblePostsByBoardIds(any());
        verify(postRepository, never()).countActiveByBoardIds(any());
    }

    @Test
    @DisplayName("userId 기반 인기 노드 조회는 요청 limit을 readable 노드 쿼리에 전달한다")
    void getTopBoardsByUserId_usesRequestedLimitForReadableQuery() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRepository.existsByUserAndIsActive(user, true)).thenReturn(true);
        when(boardRepository.findTopReadableBoardPostCounts(eq(user), eq(false), anyString(), any()))
                .thenReturn(List.of());

        List<BoardListResponse> boards = boardService.getTopBoardsByUserId(1L, 6);

        assertThat(boards).isEmpty();
        verify(boardRepository).findTopReadableBoardPostCounts(
                eq(user),
                eq(false),
                anyString(),
                eq(PageRequest.of(0, 6)));
        verify(boardRepository, never()).findTopPublicBoardPostCounts(anyString(), any());
    }

    @Test
    @DisplayName("노드 관리자는 읽을 수 있는 인기 노드 조회 쿼리를 사용한다")
    void getTopBoards_boardAdminUsesReadableQuery() {
        Board privateBoard = Board.builder()
                .boardName("Private Board")
                .boardUrl("private-board")
                .creator(user)
                .isPublic(false)
                .build();
        ReflectionTestUtils.setField(privateBoard, "boardId", 2L);
        ReflectionTestUtils.setField(privateBoard, "isActive", true);

        when(adminRepository.existsByUserAndIsActive(user, true)).thenReturn(true);
        when(boardRepository.findTopReadableBoardPostCounts(eq(user), eq(false), anyString(), any()))
                .thenReturn(List.of(topBoardPostCount(2L, 5L)));
        when(boardRepository.findByBoardIdIn(List.of(2L))).thenReturn(List.of(privateBoard));

        List<BoardListResponse> boards = boardService.getTopBoards(1L);

        assertThat(boards).extracting(BoardListResponse::getBoardUrl).containsExactly("private-board");
        assertThat(boards).extracting(BoardListResponse::getPostCount).containsExactly(5L);
        verify(adminRepository).existsByUserAndIsActive(user, true);
        verify(boardRepository).findTopReadableBoardPostCounts(eq(user), eq(false), anyString(), any());
        verify(boardRepository, never()).findTopPublicBoardPostCounts(anyString(), any());
        verify(boardRepository, never()).findTopBoardIdsByPostCount(any());
        verify(postRepository, never()).countPublicVisiblePostsByBoardIds(any());
        verify(postRepository, never()).countActiveByBoardIds(any());
    }

    @Test
    @DisplayName("권한 사용자는 읽을 수 있는 비공개 노드도 인기 노드에 포함한다")
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

        when(boardRepository.findTopReadableBoardPostCounts(eq(user), eq(true), anyString(), any()))
                .thenReturn(List.of(topBoardPostCount(2L, 8L)));
        when(boardRepository.findByBoardIdIn(List.of(2L))).thenReturn(List.of(privateBoard));

        List<BoardListResponse> boards = boardService.getTopBoards(1L);

        assertThat(boards).extracting(BoardListResponse::getBoardUrl).containsExactly("private-board");
        assertThat(boards).extracting(BoardListResponse::getPostCount).containsExactly(8L);
        verify(boardRepository).findTopReadableBoardPostCounts(eq(user), eq(true), anyString(), any());
        verify(boardRepository).findByBoardIdIn(List.of(2L));
    }

    @Test
    @DisplayName("노드 상세 조회 성공")
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
    @DisplayName("노드 공지 요약 조회 성공")
    void getNoticeSummaries_success() {
        PostSummary noticeSummary = PostSummary.builder()
                .postId(10L)
                .title("Notice")
                .build();

        when(boardRepository.findByBoardUrl("test-board")).thenReturn(Optional.of(board));
        when(postService.getNoticeSummaries(1L, null, false)).thenReturn(List.of(noticeSummary));

        List<PostSummary> summaries = boardService.getNoticeSummaries("test-board", null);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).getTitle()).isEqualTo("Notice");
        verify(postService).getNoticeSummaries(1L, null, false);
    }

    @Test
    @DisplayName("비공개 노드 공지 요약은 권한이 없으면 차단한다")
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
        verify(postService, never()).getNoticeSummaries(anyLong(), any(), anyBoolean());
    }

    @Test
    @DisplayName("비활성 노드 공지 요약은 권한이 없으면 차단한다")
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
        verify(postService, never()).getNoticeSummaries(anyLong(), any(), anyBoolean());
    }

    @Test
    @DisplayName("문의 노드 공지 요약 조회는 차단한다")
    void getNoticeSummaries_inquiryBoard_throwsBoardNotFound() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.getNoticeSummaries("inquiry", null));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BOARD_NOT_FOUND);
        verify(postService, never()).getNoticeSummaries(anyLong(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Inquiry board URL based BoardService entrypoints are blocked")
    void boardUrlServiceEntrypoints_rejectInquiryBoardUrl() {
        assertInquiryBoardBlocked(() -> boardService.getBoardDetails("inquiry", null));
        assertInquiryBoardBlocked(() -> boardService.getActiveCategories("inquiry", null));
        assertInquiryBoardBlocked(() -> boardService.updateBoard("inquiry", null, 1L));
        assertInquiryBoardBlocked(() -> boardApplicationService.updateBoardDetail("inquiry", null, 1L));
        assertInquiryBoardBlocked(() -> boardService.transferBoardManager("inquiry", "next", 1L));
        assertInquiryBoardBlocked(() -> boardApplicationService.transferBoardManagerDetail("inquiry", "next", 1L));
        assertInquiryBoardBlocked(() -> boardService.deleteBoard("inquiry", 1L));
        assertInquiryBoardBlocked(() -> boardService.subscribeBoard(1L, "inquiry"));
        assertInquiryBoardBlocked(() -> boardService.unsubscribeBoard(1L, "inquiry"));
        assertInquiryBoardBlocked(() -> boardService.createCategory("inquiry", null, 1L));
    }

    @Test
    @DisplayName("노드 구독 해제 성공")
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
    @DisplayName("읽을 수 없게 된 노드도 기존 구독은 해지할 수 있다")
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
    @DisplayName("내 구독 노드 조회는 total 과 구독 플래그를 유지한다")
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

        var result = boardService.getMySubscriptions(1L, PageRequest.of(0, 1));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).isSubscribed()).isTrue();
        assertThat(result.getContent().get(0).isSubscriptionAccessible()).isTrue();
        assertThat(result.getContent().get(0).getAccessState())
                .isEqualTo(SubscriptionBoardResponse.AccessState.ACCESSIBLE);
        assertThat(result.getContent().get(0).getInaccessibleReason()).isNull();
        verify(boardSubscriptionRepository, never()).findByUserAndBoardIn(eq(user), any());
    }

    @Test
    @DisplayName("내 구독 목록은 요청 정렬을 무시하고 고정 정렬을 사용한다")
    void getMySubscriptions_ignoresRequestedSortForFixedOrder() {
        BoardSubscription subscription = BoardSubscription.builder()
                .user(user)
                .board(board)
                .role("MEMBER")
                .sortOrder(1)
                .build();
        PageRequest requestedPageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("boardName")));
        PageRequest fixedPageable = PageRequest.of(0, 10);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findVisibleByUserOrderBySortOrderAsc(
                user, false, fixedPageable))
                .thenReturn(new PageImpl<>(List.of(subscription), fixedPageable, 1));

        var result = boardService.getMySubscriptions(1L, requestedPageable);

        assertThat(result.getPageable().getSort().isUnsorted()).isTrue();
        verify(boardSubscriptionRepository).findVisibleByUserOrderBySortOrderAsc(user, false, fixedPageable);
        verify(boardSubscriptionRepository, never()).findVisibleByUserOrderBySortOrderAsc(
                user,
                false,
                requestedPageable);
        verify(boardSubscriptionRepository, never()).findByUserAndBoardIn(eq(user), any());
    }

    @Test
    @DisplayName("내 구독 목록은 null pageable을 기본 페이지 요청으로 정규화한다")
    void getMySubscriptions_normalizesNullPageable() {
        BoardSubscription subscription = BoardSubscription.builder()
                .user(user)
                .board(board)
                .role("MEMBER")
                .sortOrder(1)
                .build();
        PageRequest fixedPageable = PageRequest.of(0, 20);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findVisibleByUserOrderBySortOrderAsc(
                user, false, fixedPageable))
                .thenReturn(new PageImpl<>(List.of(subscription), fixedPageable, 1));

        var result = boardService.getMySubscriptions(1L, null);

        assertThat(result.getPageable()).isEqualTo(fixedPageable);
        verify(boardSubscriptionRepository).findVisibleByUserOrderBySortOrderAsc(user, false, fixedPageable);
    }

    @Test
    @DisplayName("내 구독 목록은 과도한 페이지 크기를 제한한다")
    void getMySubscriptions_capsOversizedPageable() {
        BoardSubscription subscription = BoardSubscription.builder()
                .user(user)
                .board(board)
                .role("MEMBER")
                .sortOrder(1)
                .build();
        PageRequest requestedPageable = PageRequest.of(0, 200);
        PageRequest fixedPageable = PageRequest.of(0, 100);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findVisibleByUserOrderBySortOrderAsc(
                user, false, fixedPageable))
                .thenReturn(new PageImpl<>(List.of(subscription), fixedPageable, 1));

        var result = boardService.getMySubscriptions(1L, requestedPageable);

        assertThat(result.getPageable().getPageSize()).isEqualTo(100);
        verify(boardSubscriptionRepository).findVisibleByUserOrderBySortOrderAsc(user, false, fixedPageable);
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
        when(boardSubscriptionRepository.findReorderableByUser(user, false))
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
    void updateSubscriptionOrder_normalizesRequestedBoardUrls() {
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
        when(boardSubscriptionRepository.findReorderableByUser(user, false))
                .thenReturn(List.of(firstSubscription, secondSubscription));
        when(boardSubscriptionRepository.findMaxSortOrder(user)).thenReturn(2);

        boardService.updateSubscriptionOrder(1L, List.of(" second-board ", " test-board "));

        assertThat(firstSubscription.getSortOrder()).isEqualTo(2);
        assertThat(secondSubscription.getSortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("구독 순서 변경은 정규화 후 중복되는 보드 URL을 거부한다")
    void updateSubscriptionOrder_rejectsDuplicateAfterNormalization() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.updateSubscriptionOrder(1L, List.of("test-board", " test-board ")));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verify(boardSubscriptionRepository, never()).findReorderableByUser(any(), anyBoolean());
        verify(boardSubscriptionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("援щ룆 ?쒖꽌 蹂寃쎌? ?꾩옱 ?꾩껜 援щ룆 紐⑸줉怨??뺥솗???쇱튂?섏? ?딆쑝硫?嫄곕??쒕떎")
    void updateSubscriptionOrder_rejectsMismatch() {
        BoardSubscription subscription = BoardSubscription.builder()
                .user(user)
                .board(board)
                .role("MEMBER")
                .sortOrder(1)
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findReorderableByUser(user, false))
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
        when(boardSubscriptionRepository.findReorderableByUser(user, false))
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
        when(boardSubscriptionRepository.findReorderableByUser(user, false))
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
        when(boardSubscriptionRepository.findReorderableByUser(user, false))
                .thenReturn(List.of(firstSubscription, secondSubscription));
        when(boardSubscriptionRepository.findMaxSortOrder(user)).thenReturn(2);
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(boardSubscriptionRepository).flush();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.updateSubscriptionOrder(1L, List.of("second-board", "test-board")));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("문의 노드 생성 시 탈퇴한 super admin을 creator 후보에서 제외한다")
    void ensureInquiryBoard_usesActiveSuperAdminCreator() {
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
        deletedSuperAdmin.delete(java.time.LocalDateTime.of(2026, 7, 7, 12, 0));
        when(boardRepository.findByBoardUrlForUpdate("inquiry")).thenReturn(Optional.empty());
        when(userRepository.findUsableSuperAdmins()).thenReturn(List.of(activeSuperAdmin));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        when(boardRepository.findExistingBoardNamesIn(any())).thenReturn(Collections.emptyList());
        when(boardRepository.saveAndFlush(any(Board.class))).thenAnswer(invocation -> {
            Board savedBoard = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedBoard, "boardId", 2L);
            return savedBoard;
        });
        when(boardCategoryRepository.saveAndFlush(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boardService.ensureInquiryBoard(1L, "custom-inquiry-url");

        ArgumentCaptor<Board> boardCaptor = ArgumentCaptor.forClass(Board.class);
        ArgumentCaptor<BoardCategory> categoryCaptor = ArgumentCaptor.forClass(BoardCategory.class);
        verify(boardRepository).saveAndFlush(boardCaptor.capture());
        verify(boardCategoryRepository, org.mockito.Mockito.atLeastOnce()).saveAndFlush(categoryCaptor.capture());
        assertThat(boardCaptor.getValue().getCreator()).isEqualTo(activeSuperAdmin);
        assertThat(boardCaptor.getValue().getBoardName()).isEqualTo("문의");
        assertThat(boardCaptor.getValue().getDescription()).isEqualTo("운영진에게 문의를 남기는 비공개 스페이스입니다.");
        assertThat(categoryCaptor.getAllValues())
                .extracting(BoardCategory::getName)
                .containsOnly("일반");
        assertThat(categoryCaptor.getAllValues())
                .allMatch(BoardCategory::isDefaultCategory);
        verify(userRepository, org.mockito.Mockito.atLeastOnce()).findUsableSuperAdmins();
        verify(boardManagerAssignmentService, org.mockito.Mockito.atLeastOnce())
                .assignBoardManager(boardCaptor.getValue(), activeSuperAdmin);
    }

    @Test
    @DisplayName("문의 노드 이름 후보는 한 번 조회한 기존 이름을 제외하고 선택한다")
    void ensureInquiryBoard_usesFirstAvailableNameFromSingleExistingNameLookup() {
        User superAdmin = User.builder()
                .loginId("super-admin")
                .password("password")
                .email("super@test.com")
                .displayName("Super Admin")
                .build();
        ReflectionTestUtils.setField(superAdmin, "userId", 2L);
        superAdmin.grantSuperAdminRole();
        when(boardRepository.findByBoardUrlForUpdate("inquiry")).thenReturn(Optional.empty());
        when(userRepository.findUsableSuperAdmins()).thenReturn(List.of(superAdmin));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        when(boardRepository.findExistingBoardNamesIn(any()))
                .thenReturn(List.of("문의", "문의-inquiry"));
        when(boardRepository.saveAndFlush(any(Board.class))).thenAnswer(invocation -> {
            Board savedBoard = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedBoard, "boardId", 2L);
            return savedBoard;
        });
        when(boardCategoryRepository.saveAndFlush(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boardService.ensureInquiryBoard(1L, "custom-inquiry-url");

        ArgumentCaptor<Board> boardCaptor = ArgumentCaptor.forClass(Board.class);
        verify(boardRepository).saveAndFlush(boardCaptor.capture());
        assertThat(boardCaptor.getValue().getBoardName()).isEqualTo("문의-inquiry-2");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> candidatesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(boardRepository).findExistingBoardNamesIn(candidatesCaptor.capture());
        assertThat(candidatesCaptor.getValue())
                .startsWith("문의", "문의-inquiry", "문의-inquiry-2");
        verify(boardRepository, never()).existsByBoardName(anyString());
    }

    @Test
    @DisplayName("문의 노드 이름 후보가 모두 충돌하면 고정된 시간 suffix를 사용한다")
    void ensureInquiryBoard_usesClockBasedFallbackNameWhenAllCandidatesExist() {
        User superAdmin = User.builder()
                .loginId("super-admin")
                .password("password")
                .email("super@test.com")
                .displayName("Super Admin")
                .build();
        ReflectionTestUtils.setField(superAdmin, "userId", 2L);
        superAdmin.grantSuperAdminRole();
        when(boardRepository.findByBoardUrlForUpdate("inquiry")).thenReturn(Optional.empty());
        when(userRepository.findUsableSuperAdmins()).thenReturn(List.of(superAdmin));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        when(boardRepository.findExistingBoardNamesIn(any())).thenAnswer(invocation -> {
            Collection<String> candidates = invocation.getArgument(0);
            return new ArrayList<>(candidates);
        });
        when(boardRepository.saveAndFlush(any(Board.class))).thenAnswer(invocation -> {
            Board savedBoard = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedBoard, "boardId", 3L);
            return savedBoard;
        });
        when(boardCategoryRepository.saveAndFlush(any(BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boardService.ensureInquiryBoard(1L, "custom-inquiry-url");

        ArgumentCaptor<Board> boardCaptor = ArgumentCaptor.forClass(Board.class);
        verify(boardRepository).saveAndFlush(boardCaptor.capture());
        assertThat(boardCaptor.getValue().getBoardName()).isEqualTo("문의-1770000000000");
    }

    @Test
    @DisplayName("문의 노드 보정은 공개 설정과 중복 기본 카테고리 및 관리자 행을 정리한다")
    void ensureInquiryBoard_normalizesExistingBoardState() {
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

        when(boardRepository.findByBoardUrlForUpdate("inquiry")).thenReturn(Optional.of(board));
        when(userRepository.findUsableSuperAdmins()).thenReturn(List.of(superAdmin));
        when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true))
                .thenReturn(List.of(defaultCategory, duplicateCategory));

        boardService.ensureInquiryBoard(1L, "custom-inquiry-url");

        assertThat(board.getIsPublic()).isFalse();
        assertThat(board.isAgentEnabled()).isFalse();
        assertThat(defaultCategory.getSortOrder()).isEqualTo(1);
        assertThat(defaultCategory.isDefaultCategory()).isTrue();
        assertThat(duplicateCategory.getIsActive()).isFalse();
        verify(boardCategoryRepository, never()).saveAndFlush(any());
        verify(boardManagerAssignmentService).assignBoardManager(board, superAdmin);
    }

    @Test
    @DisplayName("문의 노드 보정은 기존 활성 관리자를 먼저 비활성화한 뒤 목표 관리자를 재활성화한다")
    void ensureInquiryBoard_reactivatesCanonicalManagerAfterDeactivatingWrongManager() {
        User superAdmin = User.builder()
                .loginId("super-admin")
                .password("password")
                .email("super@test.com")
                .displayName("Super Admin")
                .build();
        ReflectionTestUtils.setField(superAdmin, "userId", 2L);
        superAdmin.grantSuperAdminRole();

        when(boardRepository.findByBoardUrlForUpdate("inquiry")).thenReturn(Optional.of(board));
        when(userRepository.findUsableSuperAdmins()).thenReturn(List.of(superAdmin));
        when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true))
                .thenReturn(Collections.emptyList());
        when(boardCategoryRepository.saveAndFlush(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boardService.ensureInquiryBoard(1L, "custom-inquiry-url");

        verify(boardManagerAssignmentService).assignBoardManager(board, superAdmin);
    }

    @Test
    @DisplayName("문의 노드 생성 중 URL 중복 예외가 나면 잠금을 다시 잡아 기존 보드를 재사용한다")
    void ensureInquiryBoard_reusesBoardAfterDuplicateCreateConflict() {
        User superAdmin = User.builder()
                .loginId("super-admin")
                .password("password")
                .email("super@test.com")
                .displayName("Super Admin")
                .build();
        ReflectionTestUtils.setField(superAdmin, "userId", 2L);
        superAdmin.grantSuperAdminRole();

        when(boardRepository.findByBoardUrlForUpdate("inquiry"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(board));
        when(userRepository.findUsableSuperAdmins()).thenReturn(List.of(superAdmin));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        when(boardRepository.findExistingBoardNamesIn(any())).thenReturn(Collections.emptyList());
        when(boardRepository.saveAndFlush(any(Board.class)))
                .thenThrow(new DataIntegrityViolationException("uk_boards_board_url"));
        when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true))
                .thenReturn(Collections.emptyList());
        when(boardCategoryRepository.saveAndFlush(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boardService.ensureInquiryBoard(1L, "custom-inquiry-url");

        verify(boardRepository).saveAndFlush(any(Board.class));
        verify(boardRepository, org.mockito.Mockito.times(2)).findByBoardUrlForUpdate("inquiry");
        verify(boardCategoryRepository).saveAndFlush(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class));
        verify(boardManagerAssignmentService).assignBoardManager(board, superAdmin);
    }

    @Test
    @DisplayName("문의 노드 생성 중 이름 중복 예외가 나도 기존 보드를 다시 잠가 재사용한다")
    void ensureInquiryBoard_reusesBoardAfterDuplicateNameConflict() {
        User superAdmin = User.builder()
                .loginId("super-admin")
                .password("password")
                .email("super@test.com")
                .displayName("Super Admin")
                .build();
        ReflectionTestUtils.setField(superAdmin, "userId", 2L);
        superAdmin.grantSuperAdminRole();

        when(boardRepository.findByBoardUrlForUpdate("inquiry"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(board));
        when(userRepository.findUsableSuperAdmins()).thenReturn(List.of(superAdmin));
        when(boardRepository.findMaxSortOrder()).thenReturn(0);
        when(boardRepository.findExistingBoardNamesIn(any())).thenReturn(Collections.emptyList());
        when(boardRepository.saveAndFlush(any(Board.class)))
                .thenThrow(new DataIntegrityViolationException("uk_boards_board_name"));
        when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true))
                .thenReturn(Collections.emptyList());
        when(boardCategoryRepository.saveAndFlush(any(com.weedrice.whiteboard.domain.board.entity.BoardCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boardService.ensureInquiryBoard(1L, "custom-inquiry-url");

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
        when(boardSubscriptionRepository.findReorderableByUser(user, false))
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
    @DisplayName("구독 순서 변경은 접근 가능 구독 일부만 요청하면 거부한다")
    void updateSubscriptionOrder_rejectsRequestedSubset() {
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
        when(boardSubscriptionRepository.findReorderableByUser(user, false))
                .thenReturn(List.of(firstSubscription, secondSubscription, thirdSubscription));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.updateSubscriptionOrder(1L, List.of("third-board", "second-board")));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(firstSubscription.getSortOrder()).isEqualTo(1);
        assertThat(secondSubscription.getSortOrder()).isEqualTo(2);
        assertThat(thirdSubscription.getSortOrder()).isEqualTo(3);
        verify(boardSubscriptionRepository, never()).saveAll(any());
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

        var result = boardService.getMySubscriptions(1L, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getBoardUrl()).isEqualTo("test-board");
        assertThat(result.getContent().get(0).getAccessState())
                .isEqualTo(SubscriptionBoardResponse.AccessState.ACCESSIBLE);
        verify(boardSubscriptionRepository, never()).findByUserAndBoardIn(eq(user), any());
    }

    @Test
    @DisplayName("숨겨진 구독 포함 목록은 요청 정렬을 무시하고 고정 정렬을 사용한다")
    void getMySubscriptions_includeUnavailableIgnoresRequestedSortForFixedOrder() {
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
        PageRequest requestedPageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("boardName")));
        PageRequest fixedPageable = PageRequest.of(0, 10);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findByUserOrderBySortOrderAsc(user, fixedPageable))
                .thenReturn(new PageImpl<>(List.of(hiddenSubscription), fixedPageable, 1));

        var result = boardService.getMySubscriptions(1L, requestedPageable, true);

        assertThat(result.getPageable().getSort().isUnsorted()).isTrue();
        verify(boardSubscriptionRepository).findByUserOrderBySortOrderAsc(user, fixedPageable);
        verify(boardSubscriptionRepository, never()).findByUserOrderBySortOrderAsc(user, requestedPageable);
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
        assertThat(result.getContent().get(0).getAccessState())
                .isEqualTo(SubscriptionBoardResponse.AccessState.INACCESSIBLE);
        assertThat(result.getContent().get(0).getInaccessibleReason())
                .isEqualTo(SubscriptionBoardResponse.InaccessibleReason.PRIVATE);
        assertThat(result.getContent().get(0).getBoardUrl()).isEqualTo("hidden-board");
        verify(boardSubscriptionRepository, never()).findByUserAndBoardIn(eq(user), any());
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
        assertThat(result.getContent().get(0).getAccessState())
                .isEqualTo(SubscriptionBoardResponse.AccessState.ACCESSIBLE);
        assertThat(result.getContent().get(0).getInaccessibleReason()).isNull();
        assertThat(result.getContent().get(1).getBoardName()).isEqualTo("Admin Inactive");
        assertThat(result.getContent().get(1).isSubscriptionAccessible()).isTrue();
        assertThat(result.getContent().get(1).getAccessState())
                .isEqualTo(SubscriptionBoardResponse.AccessState.ACCESSIBLE);
        assertThat(result.getContent().get(1).getInaccessibleReason()).isNull();
        assertThat(result.getContent().get(2).getBoardName()).isNull();
        assertThat(result.getContent().get(2).isSubscriptionAccessible()).isFalse();
        assertThat(result.getContent().get(2).getAccessState())
                .isEqualTo(SubscriptionBoardResponse.AccessState.INACCESSIBLE);
        assertThat(result.getContent().get(2).getInaccessibleReason())
                .isEqualTo(SubscriptionBoardResponse.InaccessibleReason.PRIVATE);
        verify(adminRepository).findByUserAndBoard_BoardIdInAndIsActive(
                eq(user),
                argThat(boardIds -> boardIds.containsAll(List.of(3L, 4L, 5L)) && boardIds.size() == 3),
                eq(true));
        verify(adminRepository, never()).existsByUserAndBoardAndIsActive(eq(user), any(Board.class), eq(true));
        verify(boardSubscriptionRepository, never()).findByUserAndBoardIn(eq(user), any());
    }

    private CategoryRequest categoryRequest(String name, Integer sortOrder, String minWriteRole) {
        return categoryRequest(name, sortOrder, minWriteRole, null);
    }

    private CategoryRequest categoryRequest(String name, Integer sortOrder, String minWriteRole, Boolean isDefault) {
        CategoryRequest request = new CategoryRequest();
        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "sortOrder", sortOrder);
        ReflectionTestUtils.setField(request, "minWriteRole", minWriteRole);
        ReflectionTestUtils.setField(request, "isDefault", isDefault);
        return request;
    }

    private void stubCategoryBoardLock(Long categoryId) {
        when(boardCategoryRepository.findBoardIdByCategoryId(categoryId)).thenReturn(Optional.of(1L));
        when(boardRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(board));
    }

    private DataIntegrityViolationException activeCategoryConstraintViolation(String constraintName) {
        return new DataIntegrityViolationException(
                "duplicate",
                new ConstraintViolationException("duplicate", null, constraintName));
    }

    private void assertInquiryBoardBlocked(org.junit.jupiter.api.function.Executable executable) {
        BusinessException exception = assertThrows(BusinessException.class, executable);
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BOARD_NOT_FOUND);
    }

    private BoardRepository.TopBoardPostCountProjection topBoardPostCount(Long boardId, Long postCount) {
        return new BoardRepository.TopBoardPostCountProjection() {
            @Override
            public Long getBoardId() {
                return boardId;
            }

            @Override
            public Long getPostCount() {
                return postCount;
            }
        };
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
