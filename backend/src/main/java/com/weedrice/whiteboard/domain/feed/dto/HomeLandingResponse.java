package com.weedrice.whiteboard.domain.feed.dto;

import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class HomeLandingResponse {
    private List<PostSummary> posts;
    private List<PostSummary> latestPosts;
    private List<BoardListResponse> boards;
    private Stats stats;

    @Getter
    @Builder
    public static class Stats {
        private long boardCount;
        private long postCount;
        private long liveCount;
        private long onlineCount;
        private long postsToday;
        private Integer postsTodayDeltaPercent;
        private long activeBoardCount;
        private long newMembersLast24Hours;
        private long commentsToday;
    }
}
