package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscriptionId;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

        board = Board.builder()
                .boardName("Test Board")
                .boardUrl("test-board")
                .creator(user)
                .build();
    }

    @Test
    @DisplayName("활성화된 게시판 목록 조회 성공")
    void getActiveBoards_success() {
        // given
        when(boardRepository.findByIsActiveOrderBySortOrderAsc("Y")).thenReturn(Collections.singletonList(board));
        when(adminRepository.findByBoardAndRole(any(), any())).thenReturn(Optional.empty()); // Mocking admin repository
        when(postService.getLatestPostsByBoard(any(), any())).thenReturn(Collections.emptyList()); // Mocking post service

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

        // when
        boardService.subscribeBoard(userId, boardUrl);

        // then
        verify(boardSubscriptionRepository).save(any());
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
                .thenReturn(Optional.of(mock(com.weedrice.whiteboard.domain.board.entity.BoardSubscription.class)));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> boardService.subscribeBoard(userId, boardUrl));
        assertThat(exception.getErrorCode())
                .isEqualTo(com.weedrice.whiteboard.global.exception.ErrorCode.ALREADY_SUBSCRIBED);
    }

    @Test
    @DisplayName("게시판 생성 성공")
    void createBoard_success() {
        // given
        Long creatorId = 1L;
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "New Description", null);
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(user));
        when(boardRepository.existsByBoardName(request.getBoardName())).thenReturn(false);
        when(boardRepository.existsByBoardUrl(request.getBoardUrl())).thenReturn(false);
        when(boardRepository.save(any(Board.class))).thenReturn(board);

        // when
        Board createdBoard = boardService.createBoard(creatorId, request);

        // then
        assertThat(createdBoard.getBoardName()).isEqualTo("Test Board");
        verify(boardRepository).save(any(Board.class));
    }
}
