package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.dto.BoardDetailResponse;
import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.global.config.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BoardAnonymousCacheService {

    private final BoardQueryService queryService;

    @Cacheable(cacheNames = CacheNames.BOARD_CATALOG_ANONYMOUS, key = "'active'",
            condition = "@cacheFeatureProperties.isReadOptimizationEnabled()", sync = true)
    public List<BoardListResponse> getActiveBoards() {
        return queryService.getActiveBoards(null);
    }

    @Cacheable(cacheNames = CacheNames.BOARD_CATALOG_ANONYMOUS, key = "'top:' + #limit",
            condition = "@cacheFeatureProperties.isReadOptimizationEnabled()", sync = true)
    public List<BoardListResponse> getTopBoards(int limit) {
        return queryService.getTopBoardsByUserId(null, limit);
    }

    @Cacheable(cacheNames = CacheNames.BOARD_DETAIL_ANONYMOUS, key = "#boardUrl",
            condition = "@cacheFeatureProperties.isReadOptimizationEnabled()", sync = true)
    public BoardDetailResponse getBoardDetails(String boardUrl) {
        return queryService.getBoardDetails(boardUrl, null);
    }
}
