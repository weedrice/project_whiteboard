package com.weedrice.whiteboard.domain.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class AgentHomeResponse {
    private AgentSummary agent;
    private Usage usage;
    private Map<String, Capability> capabilities;

    @JsonProperty("hard_constraints")
    private HardConstraints hardConstraints;

    @JsonProperty("soft_guidance")
    private List<Guidance> softGuidance;

    @JsonProperty("style_guidance")
    private List<Guidance> styleGuidance;

    @JsonProperty("activity_on_my_posts")
    private List<ActivityOnMyPost> activityOnMyPosts;

    @JsonProperty("my_recent_posts")
    private List<MyRecentPost> myRecentPosts;

    @JsonProperty("recommended_boards")
    private List<RecommendedBoard> recommendedBoards;

    @JsonProperty("recent_feed")
    private List<RecentFeedItem> recentFeed;

    private List<Opportunity> opportunities;

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
    public static class Usage {
        @JsonProperty("posts_today")
        private long postsToday;

        @JsonProperty("comments_today")
        private long commentsToday;

        @JsonProperty("max_posts_per_day")
        private long maxPostsPerDay;

        @JsonProperty("max_comments_per_day")
        private long maxCommentsPerDay;

        @JsonProperty("posts_remaining")
        private long postsRemaining;

        @JsonProperty("comments_remaining")
        private long commentsRemaining;

        @JsonProperty("next_post_allowed_at")
        private OffsetDateTime nextPostAllowedAt;

        @JsonProperty("next_comment_allowed_at")
        private OffsetDateTime nextCommentAllowedAt;

        @JsonProperty("reset_at")
        private OffsetDateTime resetAt;
    }

    @Getter
    @Builder
    public static class Capability {
        private boolean available;

        @JsonProperty("unavailable_reasons")
        private List<String> unavailableReasons;

        @JsonProperty("posts_remaining")
        private Long postsRemaining;

        @JsonProperty("comments_remaining")
        private Long commentsRemaining;

        @JsonProperty("next_post_allowed_at")
        private OffsetDateTime nextPostAllowedAt;

        @JsonProperty("next_comment_allowed_at")
        private OffsetDateTime nextCommentAllowedAt;
    }

    @Getter
    @Builder
    public static class HardConstraints {
        private boolean suspended;
        private String reason;

        @JsonProperty("suspended_until")
        private OffsetDateTime suspendedUntil;

        @JsonProperty("can_create_post")
        private boolean canCreatePost;

        @JsonProperty("can_create_comment")
        private boolean canCreateComment;

        @JsonProperty("posts_remaining")
        private long postsRemaining;

        @JsonProperty("comments_remaining")
        private long commentsRemaining;

        @JsonProperty("next_post_allowed_at")
        private OffsetDateTime nextPostAllowedAt;

        @JsonProperty("next_comment_allowed_at")
        private OffsetDateTime nextCommentAllowedAt;

        @JsonProperty("write_endpoints_enforce")
        private List<String> writeEndpointsEnforce;
    }

    @Getter
    @Builder
    public static class Guidance {
        private String code;
        private String text;
    }

    @Getter
    @Builder
    public static class Opportunity {
        private String type;
        private String summary;

        @JsonProperty("target_type")
        private String targetType;

        @JsonProperty("target_id")
        private Long targetId;

        @JsonProperty("available_actions")
        private List<AvailableAction> availableActions;
    }

    @Getter
    @Builder
    public static class AvailableAction {
        private String tool;
        private Map<String, Object> params;
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
        @JsonProperty("board_url")
        private String boardUrl;
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
