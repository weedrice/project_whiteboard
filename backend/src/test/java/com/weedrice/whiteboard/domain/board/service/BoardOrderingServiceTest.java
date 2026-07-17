package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.lock.DomainLockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardOrderingServiceTest {

    @Mock
    private DomainLockManager domainLockManager;
    @Mock
    private BoardRepository boardRepository;
    private BoardOrderingService service;

    @BeforeEach
    void setUp() {
        service = new BoardOrderingService(domainLockManager, boardRepository);
    }

    @Test
    void reorder_locksAndAppliesCompleteOrder() {
        Board first = board(1L, 1);
        Board second = board(2L, 2);
        when(boardRepository.findAllByOrderBySortOrderAscBoardIdAsc()).thenReturn(List.of(first, second));

        service.reorder(List.of(2L, 1L));

        InOrder order = inOrder(domainLockManager, boardRepository);
        order.verify(domainLockManager).lockBoardOrder();
        order.verify(boardRepository).findAllByOrderBySortOrderAscBoardIdAsc();
        order.verify(boardRepository).flush();
        assertThat(second.getSortOrder()).isEqualTo(1);
        assertThat(first.getSortOrder()).isEqualTo(2);
    }

    @Test
    void reorder_rejectsDuplicateOrMissingIdsWithoutFlush() {
        when(boardRepository.findAllByOrderBySortOrderAscBoardIdAsc())
                .thenReturn(List.of(board(1L, 1), board(2L, 2)));

        assertThrows(BusinessException.class, () -> service.reorder(List.of(1L, 1L)));

        verify(boardRepository, never()).flush();
    }

    private static Board board(Long boardId, int sortOrder) {
        Board board = Board.builder().boardName("board-" + boardId).boardUrl("board-" + boardId)
                .sortOrder(sortOrder).build();
        ReflectionTestUtils.setField(board, "boardId", boardId);
        return board;
    }
}
