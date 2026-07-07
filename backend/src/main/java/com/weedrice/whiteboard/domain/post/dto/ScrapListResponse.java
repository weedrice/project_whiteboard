package com.weedrice.whiteboard.domain.post.dto;

import com.weedrice.whiteboard.domain.post.entity.Scrap;
import com.weedrice.whiteboard.domain.post.entity.Post;
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
        private AuthorInfo author;
        private LocalDateTime createdAt;
        private Long rowNum;
    }

    @Getter
    @Builder
    public static class AuthorInfo {
        private Long userId;
        private Long agentId;
        private String authorType;
        private String displayName;
        private String profileImageUrl;
    }

    public static ScrapListResponse from(Page<Scrap> scrapPage) {
        long totalElements = scrapPage.getTotalElements();
        int pageNumber = scrapPage.getNumber();
        int pageSize = scrapPage.getSize();

        List<ScrapSummary> content = java.util.stream.IntStream.range(0, scrapPage.getContent().size())
                .mapToObj(i -> {
                    Scrap scrap = scrapPage.getContent().get(i);
                    Post post = scrap.getPost();
                    PostSummaryFields fields = PostSummaryFields.from(post, null);
                    return ScrapSummary.builder()
                            .scrapId(post.getPostId())
                            .post(PostInfo.builder()
                                    .postId(fields.postId())
                                    .title(fields.title())
                                    .boardName(fields.boardName())
                                    .boardUrl(fields.boardUrl())
                                    .viewCount(fields.counts().viewCount())
                                    .likeCount(fields.counts().likeCount())
                                    .author(AuthorInfo.builder()
                                            .userId(fields.author().userId())
                                            .agentId(fields.author().agentId())
                                            .authorType(fields.author().authorType())
                                            .displayName(fields.author().displayName())
                                            .profileImageUrl(fields.author().profileImageUrl())
                                            .build())
                                    .createdAt(fields.createdAt())
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
