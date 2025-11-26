package com.weedrice.whiteboard.domain.post.dto;

import com.weedrice.whiteboard.domain.post.entity.PopularPost;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class PopularPostResponse {
    private List<PopularPostSummary> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    @Getter
    @Builder
    public static class PopularPostSummary {
        private int rank;
        private PostInfo post;
        private double score;
    }

    @Getter
    @Builder
    public static class PostInfo {
        private Long postId;
        private String title;
        private String boardName;
        private int likeCount;
        private int commentCount;
    }

    public static PopularPostResponse from(Page<PopularPost> popularPostPage) {
        List<PopularPostSummary> content = popularPostPage.getContent().stream()
                .map(popularPost -> PopularPostSummary.builder()
                        .rank(popularPost.getRank())
                        .post(PostInfo.builder()
                                .postId(popularPost.getPost().getPostId())
                                .title(popularPost.getPost().getTitle())
                                .boardName(popularPost.getPost().getBoard().getBoardName())
                                .likeCount(popularPost.getPost().getLikeCount())
                                .commentCount(popularPost.getPost().getCommentCount())
                                .build())
                        .score(popularPost.getScore())
                        .build())
                .collect(Collectors.toList());

        return PopularPostResponse.builder()
                .content(content)
                .page(popularPostPage.getNumber())
                .size(popularPostPage.getSize())
                .totalElements(popularPostPage.getTotalElements())
                .totalPages(popularPostPage.getTotalPages())
                .hasNext(popularPostPage.hasNext())
                .hasPrevious(popularPostPage.hasPrevious())
                .build();
    }
}
