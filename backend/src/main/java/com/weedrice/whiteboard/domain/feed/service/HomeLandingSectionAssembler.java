package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.feed.dto.FeedPostSummary;
import com.weedrice.whiteboard.domain.feed.dto.HomeLandingResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class HomeLandingSectionAssembler {

    private static final int EDITOR_PICK_START_INDEX = 1;
    private static final int EDITOR_PICK_END_INDEX = 4;
    private static final int TRENDING_START_INDEX = 1;
    private static final int TRENDING_END_INDEX = 10;
    private static final int LIVE_ACTIVITY_END_INDEX = 6;

    HomeLandingResponse assemble(List<FeedPostSummary> curatedPosts, List<FeedPostSummary> latestPosts,
            List<BoardListResponse> boards, HomeLandingResponse.Stats stats) {
        List<FeedPostSummary> safeCuratedPosts = curatedPosts == null ? List.of() : curatedPosts;
        List<FeedPostSummary> safeLatestPosts = latestPosts == null ? List.of() : latestPosts;
        List<BoardListResponse> safeBoards = boards == null ? List.of() : boards;

        return HomeLandingResponse.builder()
                .featuredPost(safeCuratedPosts.isEmpty() ? null : safeCuratedPosts.get(0))
                .editorPicks(slice(safeCuratedPosts, EDITOR_PICK_START_INDEX, EDITOR_PICK_END_INDEX))
                .trendingPosts(slice(safeCuratedPosts, TRENDING_START_INDEX, TRENDING_END_INDEX))
                .liveActivityPosts(slice(safeLatestPosts, 0, LIVE_ACTIVITY_END_INDEX))
                .boards(safeBoards)
                .stats(stats)
                .build();
    }

    private List<FeedPostSummary> slice(List<FeedPostSummary> posts, int startInclusive, int endExclusive) {
        if (posts.size() <= startInclusive) {
            return List.of();
        }
        return posts.subList(startInclusive, Math.min(posts.size(), endExclusive));
    }
}
