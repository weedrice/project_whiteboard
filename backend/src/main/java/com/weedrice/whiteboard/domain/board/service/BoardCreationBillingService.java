package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import org.springframework.stereotype.Service;

@Service
class BoardCreationBillingService {

    private static final String POINT_BOARD_CREATE_COST_CONFIG_KEY = "POINT_BOARD_CREATE_COST";
    private static final int DEFAULT_BOARD_CREATE_COST = 500;

    private final PointService pointService;
    private final GlobalConfigService globalConfigService;

    BoardCreationBillingService(PointService pointService, GlobalConfigService globalConfigService) {
        this.pointService = pointService;
        this.globalConfigService = globalConfigService;
    }

    void spendCreationCost(Long creatorId, Board board) {
        int boardCreateCost = resolveBoardCreateCost();
        if (boardCreateCost <= 0) {
            return;
        }
        pointService.spendPoint(
                creatorId,
                boardCreateCost,
                "노드 생성 (" + board.getBoardName() + ")",
                board.getBoardId(),
                "BOARD_CREATE");
    }

    private int resolveBoardCreateCost() {
        String boardCreateCostConfig = globalConfigService.getConfig(POINT_BOARD_CREATE_COST_CONFIG_KEY);
        return GlobalConfigService.parseIntConfigOrDefault(
                boardCreateCostConfig,
                DEFAULT_BOARD_CREATE_COST,
                0);
    }
}
