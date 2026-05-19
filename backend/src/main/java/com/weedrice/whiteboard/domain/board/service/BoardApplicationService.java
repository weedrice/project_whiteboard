package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.dto.BoardCreateRequest;
import com.weedrice.whiteboard.domain.board.dto.BoardDetailResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BoardApplicationService {

    private final BoardService boardService;
    private final BoardQueryService queryService;

    public BoardDetailResponse createBoardDetail(Long creatorId, BoardCreateRequest request) {
        BoardCommandResult result = boardService.createBoard(creatorId, request);
        return queryService.getBoardDetails(result.boardUrl(), creatorId);
    }

    public BoardDetailResponse updateBoardDetail(String boardUrl, BoardUpdateRequest request, Long userId) {
        BoardCommandResult result = boardService.updateBoard(boardUrl, request, userId);
        return queryService.getBoardDetails(result.boardUrl(), userId);
    }

    public BoardDetailResponse transferBoardManagerDetail(String boardUrl, String loginId, Long userId) {
        BoardCommandResult result = boardService.transferBoardManager(boardUrl, loginId, userId);
        return queryService.getBoardDetails(result.boardUrl(), userId);
    }
}
