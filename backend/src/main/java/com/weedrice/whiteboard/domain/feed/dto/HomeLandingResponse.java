package com.weedrice.whiteboard.domain.feed.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class HomeLandingResponse {
    private PostSummary featuredPost;
    private List<PostSummary> editorPicks;
    private List<PostSummary> trendingPosts;
    private List<PostSummary> liveActivityPosts;
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
