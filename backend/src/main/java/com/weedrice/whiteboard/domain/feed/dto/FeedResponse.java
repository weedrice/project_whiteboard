package com.weedrice.whiteboard.domain.feed.dto;

import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class FeedResponse {
    private List<FeedSummary> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    @Getter
    @Builder
    public static class FeedSummary {
        private Long feedId;
        private String feedType;
        private String contentType;
        private Long contentId;
        private boolean isRead;
        private LocalDateTime createdAt;
        private PostSummary post;
    }

    public static FeedResponse from(Page<UserFeed> feedPage, Map<Long, PostSummary> postSummariesById) {
        List<FeedSummary> content = feedPage.getContent().stream()
                .map(feed -> FeedSummary.builder()
                        .feedId(feed.getFeedId())
                        .feedType(feed.getFeedType())
                        .contentType(feed.getContentType())
                        .contentId(feed.getContentId())
                        .isRead(feed.getIsRead())
                        .createdAt(feed.getCreatedAt())
                        .post(resolvePostSummary(feed, postSummariesById))
                        .build())
                .toList();

        return FeedResponse.builder()
                .content(content)
                .page(feedPage.getNumber())
                .size(feedPage.getSize())
                .totalElements(feedPage.getTotalElements())
                .totalPages(feedPage.getTotalPages())
                .hasNext(feedPage.hasNext())
                .hasPrevious(feedPage.hasPrevious())
                .build();
    }

    private static PostSummary resolvePostSummary(UserFeed feed, Map<Long, PostSummary> postSummariesById) {
        if (!"POST".equals(feed.getContentType())) {
            return null;
        }
        return postSummariesById.get(feed.getContentId());
    }
}
