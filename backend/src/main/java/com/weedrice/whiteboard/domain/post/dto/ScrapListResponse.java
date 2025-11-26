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
    }

    public static ScrapListResponse from(Page<Scrap> scrapPage) {
        List<ScrapSummary> content = scrapPage.getContent().stream()
                .map(scrap -> ScrapSummary.builder()
                        .scrapId(scrap.getPost().getPostId()) // 스크랩 ID는 따로 없으므로 게시글 ID를 사용
                        .post(PostInfo.builder()
                                .postId(scrap.getPost().getPostId())
                                .title(scrap.getPost().getTitle())
                                .boardName(scrap.getPost().getBoard().getBoardName())
                                .build())
                        .remark(scrap.getRemark())
                        .createdAt(scrap.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return ScrapListResponse.builder()
                .content(content)
                .page(scrapPage.getNumber())
                .size(scrapPage.getSize())
                .totalElements(scrapPage.getTotalElements())
                .totalPages(scrapPage.getTotalPages())
                .hasNext(scrapPage.hasNext())
                .hasPrevious(scrapPage.hasPrevious())
                .build();
    }
}
