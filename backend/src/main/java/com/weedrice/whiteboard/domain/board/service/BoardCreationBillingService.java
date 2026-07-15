package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class BoardCreationBillingService {

    private static final String POINT_BOARD_CREATE_COST_CONFIG_KEY =
            GlobalConfigService.POINT_BOARD_CREATE_COST_CONFIG_KEY;
    private static final int DEFAULT_BOARD_CREATE_COST = 500;

    private final PointService pointService;
    private final GlobalConfigService globalConfigService;
    private final MessageSource messageSource;

    void spendCreationCost(Long creatorId, Board board) {
        int boardCreateCost = resolveBoardCreateCost();
        if (boardCreateCost <= 0) {
            return;
        }
        pointService.spendPoint(
                creatorId,
                boardCreateCost,
                resolveCreationDescription(board.getBoardName()),
                board.getBoardId(),
                "BOARD_CREATE");
    }

    private String resolveCreationDescription(String boardName) {
        String fallback = "스페이스 생성 (" + boardName + ")";
        return messageSource.getMessage(
                "point.description.board-create",
                new Object[]{boardName},
                fallback,
                LocaleContextHolder.getLocale());
    }

    private int resolveBoardCreateCost() {
        String boardCreateCostConfig = globalConfigService.getConfig(POINT_BOARD_CREATE_COST_CONFIG_KEY);
        return GlobalConfigService.parseIntConfigOrDefault(
                boardCreateCostConfig,
                DEFAULT_BOARD_CREATE_COST,
                0);
    }
}
