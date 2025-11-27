package com.weedrice.whiteboard.domain.post.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PostResponse {
    private Long postId;
    private String title;
    private String contents;
    private AuthorInfo author;
    private BoardInfo board;
    private CategoryInfo category;
    private List<String> tags;
    private int viewCount;
    private int likeCount;
    private int commentCount;
    @JsonProperty("isNotice")
    private boolean isNotice;

    @JsonProperty("isNsfw")
    private boolean isNsfw;

    @JsonProperty("isSpoiler")
    private boolean isSpoiler;

    @JsonProperty("isLiked")
    private boolean isLiked; // 현재 유저의 좋아요 여부

    @JsonProperty("isScrapped")
    private boolean isScrapped; // 현재 유저의 스크랩 여부
    private Long lastReadCommentId;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    @Getter
    @Builder
    public static class AuthorInfo {
        private Long userId;
        private String displayName;
        private String profileImageUrl;
    }

    @Getter
    @Builder
    public static class BoardInfo {
        private Long boardId;
        private String boardName;
    }

    @Getter
    @Builder
    public static class CategoryInfo {
        private Long categoryId;
        private String name;
    }

    public static PostResponse from(Post post, List<String> tags, ViewHistory viewHistory, boolean isLiked,
            boolean isScrapped) {
        AuthorInfo authorInfo = AuthorInfo.builder()
                .userId(post.getUser().getUserId())
                .displayName(post.getUser().getDisplayName())
                .profileImageUrl(post.getUser().getProfileImageUrl())
                .build();

        BoardInfo boardInfo = BoardInfo.builder()
                .boardId(post.getBoard().getBoardId())
                .boardName(post.getBoard().getBoardName())
                .build();

        CategoryInfo categoryInfo = post.getCategory() != null ? CategoryInfo.builder()
                .categoryId(post.getCategory().getCategoryId())
                .name(post.getCategory().getName())
                .build() : null;

        Long lastReadCommentId = null;
        if (viewHistory != null && viewHistory.getLastReadComment() != null) {
            lastReadCommentId = viewHistory.getLastReadComment().getCommentId();
        }

        return PostResponse.builder()
                .postId(post.getPostId())
                .title(post.getTitle())
                .contents(post.getContents())
                .author(authorInfo)
                .board(boardInfo)
                .category(categoryInfo)
                .tags(tags)
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .isNotice("Y".equals(post.getIsNotice()))
                .isNsfw("Y".equals(post.getIsNsfw()))
                .isSpoiler("Y".equals(post.getIsSpoiler()))
                .isLiked(isLiked)
                .isScrapped(isScrapped)
                .lastReadCommentId(lastReadCommentId)
                .createdAt(post.getCreatedAt())
                .modifiedAt(post.getModifiedAt())
                .build();
    }
}
