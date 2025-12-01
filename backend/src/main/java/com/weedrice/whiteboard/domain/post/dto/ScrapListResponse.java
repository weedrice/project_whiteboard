package com.weedrice.whiteboard.domain.post.dto;

import com.weedrice.whiteboard.domain.post.entity.Scrap;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class ScrapListResponse {
    private List<ScrapSummary> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;
    private boolean last;

    @Getter
    @Builder
    public static class ScrapSummary {
        private Long scrapId;
        private PostInfo post;
        private String remark;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class PostInfo {
        private Long postId;
        private String title;
        private String boardName;
        private String boardUrl;
        private int viewCount;
        private int likeCount;
        private String author;
        private LocalDateTime createdAt;
        private Long rowNum;
    }

    public static ScrapListResponse from(Page<Scrap> scrapPage) {
        long totalElements = scrapPage.getTotalElements();
        int pageNumber = scrapPage.getNumber();
        int pageSize = scrapPage.getSize();

        List<ScrapSummary> content = java.util.stream.IntStream.range(0, scrapPage.getContent().size())
                .mapToObj(i -> {
                    Scrap scrap = scrapPage.getContent().get(i);
                    return ScrapSummary.builder()
                        .scrapId(scrap.getPost().getPostId())
                        .post(PostInfo.builder()
                                .postId(scrap.getPost().getPostId())
                                .title(scrap.getPost().getTitle())
                                .boardName(scrap.getPost().getBoard().getBoardName())
                                .boardUrl(scrap.getPost().getBoard().getBoardUrl())
                                .viewCount(scrap.getPost().getViewCount())
                                .likeCount(scrap.getPost().getLikeCount())
                                .author(scrap.getPost().getUser().getDisplayName())
                                .createdAt(scrap.getPost().getCreatedAt())
                                .rowNum(totalElements - ((long) pageNumber * pageSize) - i)
                                .build())
                        .remark(scrap.getRemark())
                        .createdAt(scrap.getCreatedAt())
                        .build();
                })
                .collect(Collectors.toList());

        return ScrapListResponse.builder()
                .content(content)
                .page(scrapPage.getNumber())
                .size(scrapPage.getSize())
                .totalElements(scrapPage.getTotalElements())
                .totalPages(scrapPage.getTotalPages())
                .hasNext(scrapPage.hasNext())
                .hasPrevious(scrapPage.hasPrevious())
                .last(scrapPage.isLast())
                .build();
    }
}
