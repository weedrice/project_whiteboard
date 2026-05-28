package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.feed.dto.FeedPostSummary;
import com.weedrice.whiteboard.domain.feed.dto.HomeLandingResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class HomeLandingSectionAssembler {

    private static final String SECTION_TYPE_FEATURED = "FEATURED";
    private static final String SECTION_TYPE_EDITOR_PICKS = "EDITOR_PICKS";
    private static final String SECTION_TYPE_TRENDING = "TRENDING";
    private static final String SECTION_TYPE_LIVE_ACTIVITY = "LIVE_ACTIVITY";
    private static final int FEATURED_LIMIT = 1;
    private static final int EDITOR_PICK_LIMIT = 3;
    private static final int TRENDING_LIMIT = 9;
    private static final int LIVE_ACTIVITY_LIMIT = 6;
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
                .sections(List.of(
                        section(SECTION_TYPE_FEATURED, slice(safeCuratedPosts, 0, FEATURED_LIMIT), FEATURED_LIMIT),
                        section(SECTION_TYPE_EDITOR_PICKS,
                                slice(safeCuratedPosts, EDITOR_PICK_START_INDEX, EDITOR_PICK_END_INDEX),
                                EDITOR_PICK_LIMIT),
                        section(SECTION_TYPE_TRENDING,
                                slice(safeCuratedPosts, TRENDING_START_INDEX, TRENDING_END_INDEX),
                                TRENDING_LIMIT),
                        section(SECTION_TYPE_LIVE_ACTIVITY,
                                slice(safeLatestPosts, 0, LIVE_ACTIVITY_END_INDEX),
                                LIVE_ACTIVITY_LIMIT)))
                .boards(safeBoards)
                .stats(stats)
                .build();
    }

    private HomeLandingResponse.Section section(String sectionType, List<FeedPostSummary> items, int limit) {
        return HomeLandingResponse.Section.builder()
                .sectionType(sectionType)
                .items(items)
                .limit(limit)
                .build();
    }

    private List<FeedPostSummary> slice(List<FeedPostSummary> posts, int startInclusive, int endExclusive) {
        if (posts.size() <= startInclusive) {
            return List.of();
        }
        return posts.subList(startInclusive, Math.min(posts.size(), endExclusive));
    }
}
