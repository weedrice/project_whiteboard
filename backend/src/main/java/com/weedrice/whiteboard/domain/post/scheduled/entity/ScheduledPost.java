package com.weedrice.whiteboard.domain.post.scheduled.entity;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.converter.BooleanToYNConverter;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "scheduled_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduledPost extends BaseTimeEntity {
    public static final String STATUS_SCHEDULED = "SCHEDULED";
    public static final String STATUS_PUBLISHING = "PUBLISHING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String STATUS_FAILED = "FAILED";
    public static final List<String> PROTECTED_DRAFT_STATUSES = List.of(
            STATUS_SCHEDULED, STATUS_PUBLISHING, STATUS_FAILED);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scheduled_post_id")
    private Long scheduledPostId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "contents", nullable = false, columnDefinition = "TEXT")
    private String contents;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_notice", nullable = false, length = 1)
    private Boolean isNotice;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_nsfw", nullable = false, length = 1)
    private Boolean isNsfw;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_spoiler", nullable = false, length = 1)
    private Boolean isSpoiler;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_secret", nullable = false, length = 1)
    private Boolean isSecret;

    @Column(name = "tags_json", columnDefinition = "TEXT")
    private String tagsJson;

    @Column(name = "file_ids_json", columnDefinition = "TEXT")
    private String fileIdsJson;

    @Column(name = "poll_json", columnDefinition = "TEXT")
    private String pollJson;

    @Column(name = "series_id")
    private Long seriesId;

    @Column(name = "draft_id")
    private Long draftId;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "published_post_id")
    private Long publishedPostId;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Builder
    public ScheduledPost(User user, Board board, Long categoryId, String title, String contents,
            boolean isNotice, boolean isNsfw, boolean isSpoiler, boolean isSecret,
            String tagsJson, String fileIdsJson, String pollJson, Long seriesId, Long draftId,
            LocalDateTime scheduledAt) {
        this.user = user;
        this.board = board;
        this.categoryId = categoryId;
        this.title = title;
        this.contents = contents;
        this.isNotice = isNotice;
        this.isNsfw = isNsfw;
        this.isSpoiler = isSpoiler;
        this.isSecret = isSecret;
        this.tagsJson = tagsJson;
        this.fileIdsJson = fileIdsJson;
        this.pollJson = pollJson;
        this.seriesId = seriesId;
        this.draftId = draftId;
        this.scheduledAt = scheduledAt;
        this.status = STATUS_SCHEDULED;
    }

    public void update(Long categoryId, String title, String contents, boolean isNotice, boolean isNsfw,
            boolean isSpoiler, boolean isSecret, String tagsJson, String fileIdsJson, String pollJson,
            Long seriesId, Long draftId, LocalDateTime scheduledAt) {
        this.categoryId = categoryId;
        this.title = title;
        this.contents = contents;
        this.isNotice = isNotice;
        this.isNsfw = isNsfw;
        this.isSpoiler = isSpoiler;
        this.isSecret = isSecret;
        this.tagsJson = tagsJson;
        this.fileIdsJson = fileIdsJson;
        this.pollJson = pollJson;
        this.seriesId = seriesId;
        this.draftId = draftId;
        this.scheduledAt = scheduledAt;
        this.status = STATUS_SCHEDULED;
        this.failureReason = null;
        this.processingStartedAt = null;
    }

    public boolean isEditable() {
        return STATUS_SCHEDULED.equals(status) || STATUS_FAILED.equals(status);
    }

    public void expireFailed(LocalDateTime canceledAt) {
        if (!STATUS_FAILED.equals(status)) {
            return;
        }
        this.status = STATUS_CANCELED;
        this.canceledAt = canceledAt;
        this.processingStartedAt = null;
        this.draftId = null;
    }
}
