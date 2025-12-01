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
                .isNotice("Y".equals(post.getIsNotice()))
                .isNsfw("Y".equals(post.getIsNsfw()))
                .isSpoiler("Y".equals(post.getIsSpoiler()))
                .createdAt(post.getCreatedAt())
                .boardUrl(post.getBoard().getBoardUrl())
                .boardName(post.getBoard().getBoardName())
                .build();
    }
}
