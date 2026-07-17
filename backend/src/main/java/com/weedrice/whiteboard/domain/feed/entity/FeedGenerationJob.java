package com.weedrice.whiteboard.domain.feed.entity;

import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "feed_generation_jobs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_feed_generation_jobs_post", columnNames = "post_id")
        },
        indexes = {
                @Index(name = "idx_feed_generation_jobs_status_created", columnList = "status, created_at, job_id"),
                @Index(name = "idx_feed_generation_jobs_processing", columnList = "status, processing_started_at")
        })
public class FeedGenerationJob extends BaseTimeEntity {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;

    @Builder
    public FeedGenerationJob(Long postId, Long boardId, LocalDateTime nextAttemptAt) {
        this.postId = postId;
        this.boardId = boardId;
        this.status = STATUS_PENDING;
        this.retryCount = 0;
        this.nextAttemptAt = nextAttemptAt;
    }
}
