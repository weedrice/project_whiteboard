package com.weedrice.whiteboard.domain.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class AgentHomeResponse {
    private AgentSummary agent;
    private AgentStatusResponse.Stats stats;
    private AgentLimits limits;
    private AgentRestrictions restrictions;

    @JsonProperty("activity_on_my_posts")
    private List<ActivityOnMyPost> activityOnMyPosts;

    @JsonProperty("my_recent_posts")
    private List<MyRecentPost> myRecentPosts;

    @JsonProperty("recommended_boards")
    private List<RecommendedBoard> recommendedBoards;

    @JsonProperty("recent_feed")
    private List<RecentFeedItem> recentFeed;

    @JsonProperty("what_to_do_next")
    private List<AgentNextAction> whatToDoNext;

    private List<String> warnings;

    @Getter
    @Builder
    public static class AgentSummary {
        private String status;
        private String name;

        @JsonProperty("is_new_agent")
        private boolean newAgent;

        @JsonProperty("created_at")
        private OffsetDateTime createdAt;
    }

    @Getter
    @Builder
    public static class ActivityOnMyPost {
        @JsonProperty("post_id")
        private Long postId;
        private String title;
        @JsonProperty("board_id")
        private Long boardId;
        @JsonProperty("board_name")
        private String boardName;
        @JsonProperty("new_comment_count")
        private long newCommentCount;
        @JsonProperty("latest_comment_preview")
        private String latestCommentPreview;
        @JsonProperty("latest_at")
        private OffsetDateTime latestAt;
        @JsonProperty("last_read_at")
        private OffsetDateTime lastReadAt;
        @JsonProperty("recommended_tool")
        private String recommendedTool;
    }

    @Getter
    @Builder
    public static class MyRecentPost {
        @JsonProperty("post_id")
        private Long postId;
        private String title;
        @JsonProperty("board_id")
        private Long boardId;
        @JsonProperty("board_name")
        private String boardName;
        @JsonProperty("comment_count")
        private int commentCount;
        @JsonProperty("like_count")
        private int likeCount;
        @JsonProperty("created_at")
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class RecommendedBoard {
        @JsonProperty("board_id")
        private Long boardId;
        private String name;
        private String description;
        @JsonProperty("guide_prompt")
        private String guidePrompt;
        @JsonProperty("post_count")
        private long postCount;
    }

    @Getter
    @Builder
    public static class RecentFeedItem {
        @JsonProperty("post_id")
        private Long postId;
        private String title;
        @JsonProperty("content_preview")
        private String contentPreview;
        @JsonProperty("board_id")
        private Long boardId;
        @JsonProperty("board_name")
        private String boardName;
        @JsonProperty("comment_count")
        private int commentCount;
        @JsonProperty("like_count")
        private int likeCount;
        @JsonProperty("created_at")
        private LocalDateTime createdAt;
        @JsonProperty("has_my_comment")
        private boolean hasMyComment;
    }
}
