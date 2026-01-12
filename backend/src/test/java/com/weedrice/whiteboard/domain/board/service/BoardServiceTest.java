package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscriptionId;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.post.repository.DraftPostRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
import com.weedrice.whiteboard.domain.point.repository.PointHistoryRepository;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock
    private BoardRepository boardRepository;
    @Mock
    private BoardSubscriptionRepository boardSubscriptionRepository;
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
    private UserPointRepository userPointRepository;
    @Mock
    private PointHistoryRepository pointHistoryRepository;
    @Mock
    private GlobalConfigService globalConfigService;

    @InjectMocks
    private BoardService boardService;

    private User user;
    private Board board;

    @BeforeEach
    void setUp() {
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
    @DisplayName("활성화된 게시판 목록 조회 성공")
    void getActiveBoards_success() {
        // given
        when(boardRepository.findByIsActiveOrderBySortOrderAsc(true)).thenReturn(Collections.singletonList(board));
        when(boardRepository.findByBoardUrl(board.getBoardUrl())).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true)).thenReturn(Collections.emptyList());
        when(adminRepository.findByBoardAndRole(any(), any())).thenReturn(Optional.empty());
        when(postService.getLatestPostsByBoard(anyLong(), anyInt(), any())).thenReturn(Collections.emptyList());

        // when
        List<BoardResponse> activeBoards = boardService.getActiveBoards(null);

        // then
        assertThat(activeBoards).hasSize(1);
        assertThat(activeBoards.get(0).getBoardName()).isEqualTo("Test Board");
    }

    @Test
    @DisplayName("게시판 구독 성공")
    void subscribeBoard_success() {
        // given
        Long userId = 1L;
        String boardUrl = "test-board";
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl(boardUrl)).thenReturn(Optional.of(board));
        when(boardSubscriptionRepository.findById(any(BoardSubscriptionId.class))).thenReturn(Optional.empty());
        when(boardSubscriptionRepository.findMaxSortOrder(user)).thenReturn(0);

        // when
        boardService.subscribeBoard(userId, boardUrl);

        // then
        verify(boardSubscriptionRepository).save(any(BoardSubscription.class));
    }

    @Test
    @DisplayName("게시판 구독 실패 - 이미 구독한 경우")
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
    @DisplayName("게시판 생성 성공")
    void createBoard_success() {
        // given
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null);
        com.weedrice.whiteboard.domain.point.entity.UserPoint userPoint = 
                com.weedrice.whiteboard.domain.point.entity.UserPoint.builder().user(user).build();
        ReflectionTestUtils.setField(userPoint, "currentPoint", 1000);
        
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(boardRepository.existsByBoardName(request.getBoardName())).thenReturn(false);
        when(boardRepository.existsByBoardUrl(request.getBoardUrl())).thenReturn(false);
        when(userPointRepository.findById(creatorId)).thenReturn(Optional.of(userPoint));
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
        verify(boardRepository).save(any(Board.class));
        verify(boardCategoryRepository).save(any());
        verify(adminRepository).save(any());
    }

    @Test
    @DisplayName("인기 게시판 목록 조회 성공")
    void getTopBoards_success() {
        // given
        when(boardRepository.findTopBoardsByPostCount(any())).thenReturn(Collections.singletonList(board));
        when(boardRepository.findByBoardUrl(board.getBoardUrl())).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true)).thenReturn(Collections.emptyList());
        when(adminRepository.findByBoardAndRole(any(), any())).thenReturn(Optional.empty());
        when(postService.getLatestPostsByBoard(anyLong(), anyInt(), any())).thenReturn(Collections.emptyList());

        // when
        List<BoardResponse> boards = boardService.getTopBoards(null);

        // then
        assertThat(boards).hasSize(1);
        verify(boardRepository).findTopBoardsByPostCount(any());
    }

    @Test
    @DisplayName("게시판 상세 조회 성공")
    void getBoardDetails_success() {
        // given
        String boardUrl = "test-board";
        when(boardRepository.findByBoardUrl(boardUrl)).thenReturn(Optional.of(board));
        when(boardSubscriptionRepository.countByBoard(board)).thenReturn(5L);
        when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(board.getBoardId(), true)).thenReturn(Collections.emptyList());
        when(adminRepository.findByBoardAndRole(any(), any())).thenReturn(Optional.empty());
        when(postService.getLatestPostsByBoard(anyLong(), anyInt(), any())).thenReturn(Collections.emptyList());

        // when
        BoardResponse response = boardService.getBoardDetails(boardUrl, null);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getBoardName()).isEqualTo("Test Board");
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
}
