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

    @JsonProperty("isSecret")
    private boolean isSecret;

    @JsonProperty("isLiked")
    private boolean isLiked; // 현재 유저의 좋아요 여부

    @JsonProperty("isScrapped")
    private boolean isScrapped; // 현재 유저의 스크랩 여부
    private Long lastReadCommentId;
    private LocalDateTime lastViewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private List<String> imageUrls;
    private Integer boardListPage;

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
    public static class BoardInfo {
        private Long boardId;
        private String boardName;
        private String boardUrl;
        @JsonProperty("isAdmin")
        private boolean isAdmin;
    }

    @Getter
    @Builder
    public static class CategoryInfo {
        private Long categoryId;
        private String name;
    }

    public static PostResponse from(Post post, List<String> tags, ViewHistory viewHistory, boolean isLiked,
            boolean isScrapped, List<String> imageUrls, boolean isAdmin) {
        return from(post, tags, viewHistory, isLiked, isScrapped, imageUrls, isAdmin, null);
    }

    public static PostResponse from(Post post, List<String> tags, ViewHistory viewHistory, boolean isLiked,
            boolean isScrapped, List<String> imageUrls, boolean isAdmin, Integer boardListPage) {
        return from(post, tags, viewHistory, isLiked, isScrapped, imageUrls, isAdmin, boardListPage, null);
    }

    public static PostResponse from(Post post, List<String> tags, ViewHistory viewHistory, boolean isLiked,
            boolean isScrapped, List<String> imageUrls, boolean isAdmin, Integer boardListPage,
            Integer viewCountOverride) {
        AuthorInfo authorInfo = AuthorInfo.builder()
                .userId(post.getUser().getUserId())
                .agentId(post.getAgent() != null ? post.getAgent().getAgentId() : null)
                .authorType(post.getAgent() != null ? "AGENT" : "USER")
                .displayName(post.getAgent() != null ? post.getAgent().getName() : post.getUser().getDisplayName())
                .profileImageUrl(post.getAgent() != null ? null : post.getUser().getProfileImageUrl())
                .build();

        BoardInfo boardInfo = BoardInfo.builder()
                .boardId(post.getBoard().getBoardId())
                .boardName(post.getBoard().getBoardName())
                .boardUrl(post.getBoard().getBoardUrl())
                .isAdmin(isAdmin)
                .build();

        CategoryInfo categoryInfo = post.getCategory() != null ? CategoryInfo.builder()
                .categoryId(post.getCategory().getCategoryId())
                .name(post.getCategory().getName())
                .build() : null;

        Long lastReadCommentId = null;
        LocalDateTime lastViewedAt = viewHistory != null ? viewHistory.getModifiedAt() : null;
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
                .viewCount(viewCountOverride != null ? viewCountOverride : post.getViewCount())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .isNotice(post.getIsNotice())
                .isNsfw(post.getIsNsfw())
                .isSpoiler(post.getIsSpoiler())
                .isSecret(post.getIsSecret())
                .isLiked(isLiked)
                .isScrapped(isScrapped)
                .lastReadCommentId(lastReadCommentId)
                .lastViewedAt(lastViewedAt)
                .createdAt(post.getCreatedAt())
                .modifiedAt(post.getModifiedAt())
                .imageUrls(imageUrls)
                .boardListPage(boardListPage)
                .build();
    }
}
