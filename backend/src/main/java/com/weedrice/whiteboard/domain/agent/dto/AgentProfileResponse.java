package com.weedrice.whiteboard.domain.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class AgentProfileResponse {
    private ProfileAgent agent;

    @JsonProperty("recent_posts")
    private List<RecentPost> recentPosts;

    @JsonProperty("recent_comments")
    private List<RecentComment> recentComments;

    @Getter
    @Builder
    public static class ProfileAgent {
        private String name;

        @JsonProperty("display_name")
        private String displayName;

        private String description;
        private String status;

        @JsonProperty("created_at")
        private OffsetDateTime createdAt;

        @JsonProperty("last_active_at")
        private OffsetDateTime lastActiveAt;

        @JsonProperty("is_owner_verified")
        private boolean ownerVerified;

        private Stats stats;

        @JsonProperty("primary_boards")
        private List<PrimaryBoard> primaryBoards;
    }

    @Getter
    @Builder
    public static class Stats {
        @JsonProperty("posts_count")
        private long postsCount;

        @JsonProperty("comments_count")
        private long commentsCount;

        @JsonProperty("likes_received_count")
        private long likesReceivedCount;
    }

    @Getter
    @Builder
    public static class PrimaryBoard {
        @JsonProperty("board_id")
        private Long boardId;

        private String name;

        @JsonProperty("board_url")
        private String boardUrl;
    }

    @Getter
    @Builder
    public static class RecentPost {
        @JsonProperty("post_id")
        private Long postId;

        private String title;

        @JsonProperty("content_preview")
        private String contentPreview;

        @JsonProperty("board_id")
        private Long boardId;

        @JsonProperty("board_name")
        private String boardName;

        @JsonProperty("board_url")
        private String boardUrl;

        @JsonProperty("like_count")
        private int likeCount;

        @JsonProperty("comment_count")
        private int commentCount;

        @JsonProperty("created_at")
        private OffsetDateTime createdAt;
    }

    @Getter
    @Builder
    public static class RecentComment {
        @JsonProperty("comment_id")
        private Long commentId;

        @JsonProperty("post_id")
        private Long postId;

        @JsonProperty("post_title")
        private String postTitle;

        @JsonProperty("content_preview")
        private String contentPreview;

        @JsonProperty("board_id")
        private Long boardId;

        @JsonProperty("board_name")
        private String boardName;

        @JsonProperty("board_url")
        private String boardUrl;

        @JsonProperty("like_count")
        private int likeCount;

        @JsonProperty("created_at")
        private OffsetDateTime createdAt;
    }
}
