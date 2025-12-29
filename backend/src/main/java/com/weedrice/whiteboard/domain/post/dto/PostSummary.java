package com.weedrice.whiteboard.domain.post.dto;

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
    private String title;
    private AuthorInfo author;
    private CategoryInfo category;
    private int viewCount;
    private int likeCount;
    private int commentCount;
    private boolean isNotice;
    private boolean isNsfw;
    private boolean isSpoiler;
    private LocalDateTime createdAt;
    private String boardUrl;
    private String boardName;
    private String thumbnailUrl;
    private String boardIconUrl;
    private boolean isLiked;
    private boolean isScrapped;
    private boolean isSubscribed;
    private boolean hasImage;
    private String summary;

    @Getter
    @Builder
    public static class AuthorInfo {
        private Long userId;
        private String displayName;
        private String profileImageUrl;
    }

    @Getter
    @Builder
    public static class CategoryInfo {
        private Long categoryId;
        private String name;
    }

    public static PostSummary from(Post post) {
        String summary = post.getContents().replaceAll("<[^>]*>", "").trim();
        if (summary.length() > 1000) {
            summary = summary.substring(0, 1000);
        }
        return from(post, null, null, false, false, false, false, summary);
    }

    public static PostSummary from(Post post, String thumbnailUrl, String boardIconUrl, boolean isLiked,
            boolean isScrapped, boolean isSubscribed, boolean hasImage, String summary) {
        return PostSummary.builder()
                .postId(post.getPostId())
                .title(post.getTitle())
                .author(AuthorInfo.builder()
                        .userId(post.getUser().getUserId())
                        .displayName(post.getUser().getDisplayName())
                        .profileImageUrl(post.getUser().getProfileImageUrl())
                        .build())
                .category(post.getCategory() != null ? CategoryInfo.builder()
                        .categoryId(post.getCategory().getCategoryId())
                        .name(post.getCategory().getName())
                        .build() : null)
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .isNotice(post.getIsNotice())
                .isNsfw(post.getIsNsfw())
                .isSpoiler(post.getIsSpoiler())
                .createdAt(post.getCreatedAt())
                .boardUrl(post.getBoard().getBoardUrl())
                .boardName(post.getBoard().getBoardName())
                .thumbnailUrl(thumbnailUrl)
                .boardIconUrl(boardIconUrl)
                .isLiked(isLiked)
                .isScrapped(isScrapped)
                .isSubscribed(isSubscribed)
                .hasImage(hasImage)
                .summary(summary)
                .build();
    }
}
