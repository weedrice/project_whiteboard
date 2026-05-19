package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardDetailResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardApplicationServiceTest {

    @Mock
    private BoardService boardService;

    @Mock
    private BoardQueryService queryService;

    @Mock
    private BoardDetailResponse detailResponse;

    private BoardApplicationService boardApplicationService;

    @BeforeEach
    void setUp() {
        boardApplicationService = new BoardApplicationService(boardService, queryService);
    }

    @Test
    void createBoardDetail_queriesDetailWithCreatedBoardUrl() {
        BoardCreateRequest request = new BoardCreateRequest("New Board", "new-board", "Description", null, true);
        when(boardService.createBoard(1L, request)).thenReturn(new BoardCommandResult("created-board"));
        when(queryService.getBoardDetails("created-board", 1L)).thenReturn(detailResponse);

        BoardDetailResponse response = boardApplicationService.createBoardDetail(1L, request);

        assertThat(response).isSameAs(detailResponse);
        InOrder inOrder = inOrder(boardService, queryService);
        inOrder.verify(boardService).createBoard(1L, request);
        inOrder.verify(queryService).getBoardDetails("created-board", 1L);
    }

    @Test
    void updateBoardDetail_queriesDetailWithUpdatedBoardUrl() {
        BoardUpdateRequest request = new BoardUpdateRequest();
        when(boardService.updateBoard("old-board", request, 1L))
                .thenReturn(new BoardCommandResult("updated-board"));
        when(queryService.getBoardDetails("updated-board", 1L)).thenReturn(detailResponse);

        BoardDetailResponse response = boardApplicationService.updateBoardDetail("old-board", request, 1L);

        assertThat(response).isSameAs(detailResponse);
        InOrder inOrder = inOrder(boardService, queryService);
        inOrder.verify(boardService).updateBoard("old-board", request, 1L);
        inOrder.verify(queryService).getBoardDetails("updated-board", 1L);
    }

    @Test
    void transferBoardManagerDetail_queriesDetailAfterTransfer() {
        when(boardService.transferBoardManager("test-board", "nextmanager", 1L))
                .thenReturn(new BoardCommandResult("test-board"));
        when(queryService.getBoardDetails("test-board", 1L)).thenReturn(detailResponse);

        BoardDetailResponse response = boardApplicationService.transferBoardManagerDetail(
                "test-board", "nextmanager", 1L);

        assertThat(response).isSameAs(detailResponse);
        InOrder inOrder = inOrder(boardService, queryService);
        inOrder.verify(boardService).transferBoardManager("test-board", "nextmanager", 1L);
        inOrder.verify(queryService).getBoardDetails("test-board", 1L);
    }
}
