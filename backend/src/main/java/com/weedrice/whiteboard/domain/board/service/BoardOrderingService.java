package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.lock.DomainLockManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class BoardOrderingService {

    private final DomainLockManager domainLockManager;
    private final BoardRepository boardRepository;

    void reorder(List<Long> boardIds) {
        domainLockManager.lockBoardOrder();
        List<Board> boards = boardRepository.findAllByOrderBySortOrderAscBoardIdAsc();
        validateCompleteOrder(boards, boardIds);

        Map<Long, Board> boardsById = boards.stream()
                .collect(Collectors.toMap(Board::getBoardId, Function.identity()));
        for (int index = 0; index < boardIds.size(); index++) {
            boardsById.get(boardIds.get(index)).updateSortOrder(index + 1);
        }
        boardRepository.flush();
    }

    private void validateCompleteOrder(List<Board> boards, List<Long> boardIds) {
        if (boardIds == null || boardIds.isEmpty() || boardIds.size() != boards.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Set<Long> requestedIds = new HashSet<>(boardIds);
        Set<Long> actualIds = boards.stream().map(Board::getBoardId).collect(Collectors.toSet());
        if (requestedIds.size() != boardIds.size() || !requestedIds.equals(actualIds)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
