package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.service.BoardManagerAssignmentService;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.stereotype.Service;

@Service
class BoardCreationInitializer {

    private final BoardAiInfoService boardAiInfoService;
    private final BoardCategoryRepository boardCategoryRepository;
    private final BoardManagerAssignmentService boardManagerAssignmentService;

    BoardCreationInitializer(BoardAiInfoService boardAiInfoService,
                             BoardCategoryRepository boardCategoryRepository,
                             BoardManagerAssignmentService boardManagerAssignmentService) {
        this.boardAiInfoService = boardAiInfoService;
        this.boardCategoryRepository = boardCategoryRepository;
        this.boardManagerAssignmentService = boardManagerAssignmentService;
    }

    void initialize(Board board, User creator, String guidePrompt) {
        boardAiInfoService.upsertBoardAiInfoIfEnabled(board, guidePrompt, true);
        createDefaultCategory(board);
        boardManagerAssignmentService.assignBoardManager(board, creator);
    }

    private void createDefaultCategory(Board board) {
        BoardCategory defaultCategory = BoardCategory.builder()
                .board(board)
                .name(BoardDefaultCategoryNames.DEFAULT_CATEGORY_NAME)
                .sortOrder(1)
                .isDefault(true)
                .build();
        boardCategoryRepository.save(defaultCategory);
    }
}
