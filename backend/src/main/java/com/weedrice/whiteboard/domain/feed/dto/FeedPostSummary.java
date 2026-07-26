package com.weedrice.whiteboard.domain.feed.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class FeedPostSummary {
    private Long postId;
    private Long boardId;
    private Long categoryId;
    private String title;
    private AuthorInfo author;
    private CategoryInfo category;
    private int viewCount;
    private int likeCount;
    private int commentCount;
    @JsonProperty("isNotice")
    @Getter(onMethod_ = @JsonProperty("isNotice"))
    private boolean isNotice;
    @JsonProperty("isNsfw")
    @Getter(onMethod_ = @JsonProperty("isNsfw"))
    private boolean isNsfw;
    @JsonProperty("isSpoiler")
    @Getter(onMethod_ = @JsonProperty("isSpoiler"))
    private boolean isSpoiler;
    @JsonProperty("isSecret")
    @Getter(onMethod_ = @JsonProperty("isSecret"))
    private boolean isSecret;
    private LocalDateTime createdAt;
    private String boardUrl;
    private String boardName;
    private String thumbnailUrl;
    private String boardIconUrl;
    private String authorName;
    private boolean liked;
    private boolean scrapped;
    private boolean subscribed;
    private boolean hasImage;
    private String summary;
    private String contentsExcerpt;
    private String firstMediaType;
    private String firstMediaUrl;

    @Getter
    @Builder
    public static class AuthorInfo {
        private Long userId;
        private Long agentId;
        private String authorType;
        private String displayName;
        private String profileImageUrl;
    }

    @Getter
    @Builder
    public static class CategoryInfo {
        private Long categoryId;
        private String name;
    }
}
