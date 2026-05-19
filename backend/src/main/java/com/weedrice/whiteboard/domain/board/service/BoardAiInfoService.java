package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardAiInfo;
import com.weedrice.whiteboard.domain.board.repository.BoardAiInfoRepository;
import org.springframework.stereotype.Service;

@Service
class BoardAiInfoService {

    private final BoardAiInfoRepository boardAiInfoRepository;

    BoardAiInfoService(BoardAiInfoRepository boardAiInfoRepository) {
        this.boardAiInfoRepository = boardAiInfoRepository;
    }

    void upsertBoardAiInfoIfEnabled(Board board, String requestedGuidePrompt, boolean initializeFromDescription) {
        if (board == null || !board.isAgentEnabled()) {
            return;
        }

        BoardAiInfo boardAiInfo = boardAiInfoRepository.findByBoard_BoardId(board.getBoardId())
                .orElse(null);

        if (boardAiInfo == null) {
            boardAiInfoRepository.save(BoardAiInfo.builder()
                    .board(board)
                    .guidePrompt(resolveInitialGuidePrompt(board, requestedGuidePrompt, initializeFromDescription))
                    .build());
            return;
        }

        if (requestedGuidePrompt != null) {
            boardAiInfo.updateGuidePrompt(normalizeGuidePrompt(requestedGuidePrompt));
        }
    }

    private String resolveInitialGuidePrompt(Board board, String requestedGuidePrompt, boolean initializeFromDescription) {
        if (requestedGuidePrompt != null && !requestedGuidePrompt.isBlank()) {
            return normalizeGuidePrompt(requestedGuidePrompt);
        }
        if (initializeFromDescription) {
            return normalizeDescription(board.getDescription());
        }
        return "";
    }

    private String normalizeGuidePrompt(String guidePrompt) {
        return guidePrompt == null || guidePrompt.isBlank() ? "" : guidePrompt;
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? "" : description;
    }
}
