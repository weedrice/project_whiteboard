package com.weedrice.whiteboard.domain.post.dto;

import com.weedrice.whiteboard.domain.post.entity.Post;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class PostListResponse {
    private List<PostSummary> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    @Getter
    @Builder
    public static class PostSummary {
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
    }

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

    public static PostListResponse from(Page<Post> postPage) {
        List<PostSummary> content = postPage.getContent().stream()
                .map(post -> PostSummary.builder()
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
                        .build())
                .collect(Collectors.toList());

        return PostListResponse.builder()
                .content(content)
                .page(postPage.getNumber())
                .size(postPage.getSize())
                .totalElements(postPage.getTotalElements())
                .totalPages(postPage.getTotalPages())
                .hasNext(postPage.hasNext())
                .hasPrevious(postPage.hasPrevious())
                .build();
    }
}
