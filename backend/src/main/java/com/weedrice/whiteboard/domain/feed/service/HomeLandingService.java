package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.feed.dto.FeedPostSummary;
import com.weedrice.whiteboard.domain.feed.dto.HomeLandingResponse;
import com.weedrice.whiteboard.domain.post.service.PostListReadService;
import com.weedrice.whiteboard.global.config.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeLandingService {

    private static final int LANDING_BOARD_LIMIT = 7;
    private static final int LANDING_CURATED_POST_LIMIT = 16;
    private static final int LANDING_LATEST_POST_LIMIT = 6;

    private final PostListReadService postListReadService;
    private final BoardService boardService;
    private final HomeLandingStatsService statsService;
    private final Clock clock;

    @Cacheable(cacheNames = CacheNames.HOME_LANDING_ANONYMOUS,
            key = "T(com.weedrice.whiteboard.global.config.AnonymousCacheKey).landing(#period, @clock)",
            condition = "#userId == null && @cacheFeatureProperties.isReadOptimizationEnabled()", sync = true)
    public HomeLandingResponse getLanding(Long userId, String period) {
        LocalDateTime observedAt = LocalDateTime.now(clock);
        List<FeedPostSummary> curatedPosts = getCuratedPosts(userId, period);
        List<FeedPostSummary> latestPosts = getLatestPosts(userId);
        List<BoardListResponse> boards = getBoards(userId);
        HomeLandingResponse.Stats stats = statsService.getStats(observedAt);

        return HomeLandingResponse.builder()
                .curatedPosts(safeList(curatedPosts))
                .latestPosts(safeList(latestPosts))
                .boards(safeList(boards))
                .stats(stats)
                .build();
    }

    private List<FeedPostSummary> getCuratedPosts(Long userId, String period) {
        return postListReadService.getTrendingFeedPosts(PageRequest.of(0, LANDING_CURATED_POST_LIMIT), userId, period);
    }

    private List<FeedPostSummary> getLatestPosts(Long userId) {
        return postListReadService.getPublicLandingLatestFeedPosts(
                PageRequest.of(0, LANDING_LATEST_POST_LIMIT),
                userId);
    }

    private List<BoardListResponse> getBoards(Long userId) {
        return boardService.getTopBoardsByUserId(userId, LANDING_BOARD_LIMIT);
    }

    private <T> List<T> safeList(List<T> items) {
        return items == null ? List.of() : items;
    }
}
