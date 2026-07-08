package com.weedrice.whiteboard.domain.post.dto;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostSeries;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PostSeriesNavigation {
    private SeriesSummary series;
    private PostLink previousPost;
    private PostLink nextPost;

    @Getter
    @Builder
    public static class SeriesSummary {
        private Long seriesId;
        private String title;
    }

    @Getter
    @Builder
    public static class PostLink {
        private Long postId;
        private String title;
        private String boardUrl;
    }

    public static SeriesSummary seriesFrom(PostSeries series) {
        return SeriesSummary.builder()
                .seriesId(series.getSeriesId())
                .title(series.getTitle())
                .build();
    }

    public static PostLink postFrom(Post post) {
        if (post == null) {
            return null;
        }
        return PostLink.builder()
                .postId(post.getPostId())
                .title(post.getTitle())
                .boardUrl(post.getBoard().getBoardUrl())
                .build();
    }
}
