package com.weedrice.whiteboard.domain.post.scheduled.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.post.dto.PollRequest;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ScheduledPostDetailResponse {
    private Long scheduledPostId;
    private String status;
    private Long userId;
    private Long boardId;
    private String boardUrl;
    private String boardName;
    private Long categoryId;
    private Long draftId;
    private String title;
    private String contents;
    private List<String> tags;
    @Getter(onMethod_ = @JsonProperty("isNotice"))
    private boolean isNotice;

    @Getter(onMethod_ = @JsonProperty("isNsfw"))
    private boolean isNsfw;

    @Getter(onMethod_ = @JsonProperty("isSpoiler"))
    private boolean isSpoiler;

    @Getter(onMethod_ = @JsonProperty("isSecret"))
    private boolean isSecret;
    private List<Long> fileIds;
    private PollRequest poll;
    private Long seriesId;
    private LocalDateTime scheduledAt;
    private Long publishedPostId;
    private String failureReason;
    private LocalDateTime publishedAt;
    private LocalDateTime canceledAt;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static ScheduledPostDetailResponse from(
            ScheduledPost scheduledPost,
            List<String> tags,
            List<Long> fileIds,
            PollRequest poll) {
        return ScheduledPostDetailResponse.builder()
                .scheduledPostId(scheduledPost.getScheduledPostId())
                .status(scheduledPost.getStatus())
                .userId(scheduledPost.getUser().getUserId())
                .boardId(scheduledPost.getBoard().getBoardId())
                .boardUrl(scheduledPost.getBoard().getBoardUrl())
                .boardName(scheduledPost.getBoard().getBoardName())
                .categoryId(scheduledPost.getCategoryId())
                .draftId(scheduledPost.getDraftId())
                .title(scheduledPost.getTitle())
                .contents(scheduledPost.getContents())
                .tags(tags)
                .isNotice(Boolean.TRUE.equals(scheduledPost.getIsNotice()))
                .isNsfw(Boolean.TRUE.equals(scheduledPost.getIsNsfw()))
                .isSpoiler(Boolean.TRUE.equals(scheduledPost.getIsSpoiler()))
                .isSecret(Boolean.TRUE.equals(scheduledPost.getIsSecret()))
                .fileIds(fileIds)
                .poll(poll)
                .seriesId(scheduledPost.getSeriesId())
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
