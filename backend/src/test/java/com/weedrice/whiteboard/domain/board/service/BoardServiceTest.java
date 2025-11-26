package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscriptionId;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock
    private BoardRepository boardRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BoardSubscriptionRepository boardSubscriptionRepository;

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
                .creator(user)
                .build();
    }

    @Test
    @DisplayName("게시판 구독 성공")
    void subscribeBoard_success() {
        // given
        Long userId = 1L;
        Long boardId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));
        when(boardSubscriptionRepository.findById(any(BoardSubscriptionId.class))).thenReturn(Optional.empty());

        // when
        boardService.subscribeBoard(userId, boardId);

        // then
        verify(boardSubscriptionRepository).save(any(BoardSubscription.class));
    }

    @Test
    @DisplayName("게시판 구독 실패 - 이미 구독한 경우")
    void subscribeBoard_fail_alreadySubscribed() {
        // given
        Long userId = 1L;
        Long boardId = 1L;
        BoardSubscription subscription = new BoardSubscription(user, board, "MEMBER");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));
        when(boardSubscriptionRepository.findById(any(BoardSubscriptionId.class))).thenReturn(Optional.of(subscription));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> boardService.subscribeBoard(userId, boardId));
        assertThat(exception.getErrorCode().getCode()).isEqualTo("ALREADY_SUBSCRIBED");
    }

    @Test
    @DisplayName("게시판 구독 취소 성공")
    void unsubscribeBoard_success() {
        // given
        Long userId = 1L;
        Long boardId = 1L;
        BoardSubscription subscription = new BoardSubscription(user, board, "MEMBER");
        when(boardSubscriptionRepository.findById(any(BoardSubscriptionId.class))).thenReturn(Optional.of(subscription));

        // when
        boardService.unsubscribeBoard(userId, boardId);

        // then
        verify(boardSubscriptionRepository).delete(any(BoardSubscription.class));
    }

    @Test
    @DisplayName("게시판 구독 취소 실패 - 구독하지 않은 경우")
    void unsubscribeBoard_fail_notSubscribed() {
        // given
        Long userId = 1L;
        Long boardId = 1L;
        when(boardSubscriptionRepository.findById(any(BoardSubscriptionId.class))).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> boardService.unsubscribeBoard(userId, boardId));
        assertThat(exception.getErrorCode().getCode()).isEqualTo("NOT_SUBSCRIBED");
    }
}
