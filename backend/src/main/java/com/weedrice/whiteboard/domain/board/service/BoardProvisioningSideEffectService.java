package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
class BoardProvisioningSideEffectService {

    private final BoardIconAttachmentService boardIconAttachmentService;
    private final BoardCreationBillingService boardCreationBillingService;
    private final BoardCreationInitializer boardCreationInitializer;
    private final BoardAiInfoService boardAiInfoService;
    private final SemanticSearchEventPublisher semanticSearchEventPublisher;

    BoardProvisioningSideEffectService(BoardIconAttachmentService boardIconAttachmentService,
                                       BoardCreationBillingService boardCreationBillingService,
                                       BoardCreationInitializer boardCreationInitializer,
                                       BoardAiInfoService boardAiInfoService,
                                       SemanticSearchEventPublisher semanticSearchEventPublisher) {
        this.boardIconAttachmentService = boardIconAttachmentService;
        this.boardCreationBillingService = boardCreationBillingService;
        this.boardCreationInitializer = boardCreationInitializer;
        this.boardAiInfoService = boardAiInfoService;
        this.semanticSearchEventPublisher = semanticSearchEventPublisher;
    }

    void applyCreateSideEffects(BoardCreateCommandResult result) {
        boardIconAttachmentService.syncBoardIcon(result.creatorId(), result.board(), null);
        boardCreationBillingService.spendCreationCost(result.creatorId(), result.board());
        boardCreationInitializer.initialize(result.board(), result.creator(), result.guidePrompt());
    }

    void applyUpdateSideEffects(BoardUpdateCommandResult result) {
        boardIconAttachmentService.syncBoardIcon(result.currentUser().getUserId(), result.board(),
                result.previousIconUrl());
        boardAiInfoService.upsertBoardAiInfoIfEnabled(result.board(), result.guidePrompt(), false);
        if (!Objects.equals(result.previousBoardName(), result.board().getBoardName())) {
            semanticSearchEventPublisher.publishBoardContentReindex(result.board().getBoardId());
        }
    }
}
