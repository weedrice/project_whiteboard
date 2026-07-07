package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.util.BoardUrlNormalizer;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
class BoardCategoryMutationResolver {

    private final BoardRepository boardRepository;
    private final BoardCategoryRepository boardCategoryRepository;
    private final UserRepository userRepository;
    private final BoardAccessPolicy boardAccessPolicy;

    BoardCategoryMutationResolver(BoardRepository boardRepository,
                                  BoardCategoryRepository boardCategoryRepository,
                                  UserRepository userRepository,
                                  BoardAccessPolicy boardAccessPolicy) {
        this.boardRepository = boardRepository;
        this.boardCategoryRepository = boardCategoryRepository;
        this.userRepository = userRepository;
        this.boardAccessPolicy = boardAccessPolicy;
    }

    Board resolveBoardForCreate(String boardUrl, Long userId) {
        String normalizedBoardUrl = BoardUrlNormalizer.normalizeLookup(boardUrl);
        Board board = boardRepository.findByBoardUrlForUpdate(normalizedBoardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        boardAccessPolicy.validateBoardAdmin(board, getCurrentUser(userId));
        return board;
    }

    BoardCategory resolveCategoryForUpdate(Long categoryId, Long userId) {
        BoardCategory category = findCategoryForUpdate(categoryId);
        boardAccessPolicy.validateBoardAdmin(category.getBoard(), getCurrentUser(userId));
        return category;
    }

    private User getCurrentUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private BoardCategory findCategoryForUpdate(Long categoryId) {
        Long boardId = boardCategoryRepository.findBoardIdByCategoryId(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        lockBoardForUpdate(boardId);
        return boardCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void lockBoardForUpdate(Long boardId) {
        boardRepository.findByIdForUpdate(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
    }
}
