package com.weedrice.whiteboard.domain.post.dto;

import com.weedrice.whiteboard.domain.post.entity.PostSeries;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PostSeriesResponse {
    private Long seriesId;
    private String title;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static PostSeriesResponse from(PostSeries series) {
        return PostSeriesResponse.builder()
                .seriesId(series.getSeriesId())
                .title(series.getTitle())
                .description(series.getDescription())
                .createdAt(series.getCreatedAt())
                .modifiedAt(series.getModifiedAt())
                .build();
    }

    public static List<PostSeriesResponse> listFrom(List<PostSeries> seriesList) {
        return seriesList.stream().map(PostSeriesResponse::from).toList();
    }
}
