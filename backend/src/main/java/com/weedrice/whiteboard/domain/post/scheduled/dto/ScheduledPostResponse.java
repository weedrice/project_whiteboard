package com.weedrice.whiteboard.domain.post.scheduled.dto;

import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ScheduledPostResponse {
    private Long scheduledPostId;
    private String status;
    private Long userId;
    private Long boardId;
    private String boardUrl;
    private String boardName;
    private Long categoryId;
    private Long draftId;
    private String title;
    private LocalDateTime scheduledAt;
    private Long publishedPostId;
    private String failureReason;
    private LocalDateTime publishedAt;
    private LocalDateTime canceledAt;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static ScheduledPostResponse from(ScheduledPost scheduledPost) {
        return ScheduledPostResponse.builder()
                .scheduledPostId(scheduledPost.getScheduledPostId())
                .status(scheduledPost.getStatus())
                .userId(scheduledPost.getUser().getUserId())
                .boardId(scheduledPost.getBoard().getBoardId())
                .boardUrl(scheduledPost.getBoard().getBoardUrl())
                .boardName(scheduledPost.getBoard().getBoardName())
                .categoryId(scheduledPost.getCategoryId())
                .draftId(scheduledPost.getDraftId())
                .title(scheduledPost.getTitle())
                .scheduledAt(scheduledPost.getScheduledAt())
                .publishedPostId(scheduledPost.getPublishedPostId())
                .failureReason(scheduledPost.getFailureReason())
                .publishedAt(scheduledPost.getPublishedAt())
                .canceledAt(scheduledPost.getCanceledAt())
                .createdAt(scheduledPost.getCreatedAt())
                .modifiedAt(scheduledPost.getModifiedAt())
                .build();
    }
}
