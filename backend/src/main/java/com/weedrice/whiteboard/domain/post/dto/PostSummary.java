package com.weedrice.whiteboard.domain.post.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.badge.dto.BadgeCompactResponse;
import com.weedrice.whiteboard.domain.post.entity.Post;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class PostSummary {
    private Long rowNum;
    private Long postId;
    private Long boardId;
    private Long categoryId;
    private String title;
    private AuthorInfo author;
    private CategoryInfo category;
    private int viewCount;
    private int likeCount;
    private int commentCount;
    private boolean isNotice;
    private boolean isNsfw;
    @JsonProperty("isSpoiler")
    @Getter(onMethod_ = @JsonProperty("isSpoiler"))
    private boolean isSpoiler;
    @JsonProperty("isSecret")
    @Getter(onMethod_ = @JsonProperty("isSecret"))
    private boolean isSecret;
    @JsonProperty("isBlinded")
    @Getter(onMethod_ = @JsonProperty("isBlinded"))
    private boolean isBlinded;
    private String blindReason;
    private LocalDateTime pinnedAt;
    private LocalDateTime createdAt;
    private String boardUrl;
    private String boardName;
    private String thumbnailUrl;
    private String boardIconUrl;
    private boolean isLiked;
    private boolean isScrapped;
    private boolean isSubscribed;
    private boolean hasMyComment;
    private Boolean inquiryAnswered;
    private boolean hasImage;
    private String summary;

    @Getter
    @Builder
    public static class AuthorInfo {
        private Long userId;
        private Long agentId;
        private String authorType;
        private String displayName;
        private String profileImageUrl;
        private BadgeCompactResponse representativeBadge;
    }

    @Getter
    @Builder
    public static class CategoryInfo {
        private Long categoryId;
        private String name;
    }

    public static PostSummary from(Post post) {
        return from(post, null);
    }

    public static PostSummary from(Post post, String summary) {
        return from(post, null, null, false, false, false, false, summary);
    }

    public static PostSummary from(Post post, String thumbnailUrl, String boardIconUrl, boolean isLiked,
            boolean isScrapped, boolean isSubscribed, boolean hasImage, String summary) {
        PostSummaryFields fields = PostSummaryFields.from(post, boardIconUrl);
        return PostSummary.builder()
                .postId(fields.postId())
                .boardId(fields.boardId())
                .categoryId(fields.categoryId())
                .title(fields.title())
                .author(AuthorInfo.builder()
                        .userId(fields.author().userId())
                        .agentId(fields.author().agentId())
                        .authorType(fields.author().authorType())
                        .displayName(fields.author().displayName())
                        .profileImageUrl(fields.author().profileImageUrl())
                        .representativeBadge(fields.author().representativeBadge())
                        .build())
                .category(fields.category() != null ? CategoryInfo.builder()
                        .categoryId(fields.category().categoryId())
                        .name(fields.category().name())
                        .build() : null)
                .viewCount(fields.counts().viewCount())
                .likeCount(fields.counts().likeCount())
                .commentCount(fields.counts().commentCount())
                .isNotice(fields.flags().isNotice())
                .isNsfw(fields.flags().isNsfw())
                .isSpoiler(fields.flags().isSpoiler())
                .isSecret(fields.flags().isSecret())
                .isBlinded(Boolean.TRUE.equals(post.getIsBlinded()))
                .blindReason(post.getBlindReason())
                .pinnedAt(post.getPinnedAt())
                .createdAt(fields.createdAt())
                .boardUrl(fields.boardUrl())
                .boardName(fields.boardName())
                .thumbnailUrl(thumbnailUrl)
                .boardIconUrl(fields.boardIconUrl())
                .isLiked(isLiked)
                .isScrapped(isScrapped)
                .isSubscribed(isSubscribed)
                .hasImage(hasImage)
                .summary(summary)
                .build();
    }
}
