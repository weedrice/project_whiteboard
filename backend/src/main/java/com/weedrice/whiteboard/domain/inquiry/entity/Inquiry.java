package com.weedrice.whiteboard.domain.inquiry.entity;

import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "inquiries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inquiry_id")
    private Long inquiryId;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 40, nullable = false)
    private InquiryCategory category;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private InquiryStatus status;

    @Column(name = "staff_action_since")
    private LocalDateTime staffActionSince;

    @Column(name = "last_public_activity_at", nullable = false)
    private LocalDateTime lastPublicActivityAt;

    @Column(name = "first_responded_at")
    private LocalDateTime firstRespondedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by_user_id")
    private Long closedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "closure_reason", length = 30)
    private InquiryClosureReason closureReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Inquiry(Long authorUserId, InquiryCategory category, String title, LocalDateTime now) {
        this.authorUserId = authorUserId;
        this.category = category;
        this.title = title;
        this.status = InquiryStatus.NEW;
        this.staffActionSince = now;
        this.lastPublicActivityAt = now;
    }

    public void start() {
        requireStatus(InquiryStatus.NEW);
        status = InquiryStatus.IN_PROGRESS;
    }

    public InquiryStatus reply(LocalDateTime now) {
        requireNotClosed();
        InquiryStatus previous = status;
        status = InquiryStatus.RESOLVED;
        staffActionSince = null;
        if (firstRespondedAt == null) firstRespondedAt = now;
        resolvedAt = now;
        lastPublicActivityAt = now;
        clearClosure();
        return previous;
    }

    public InquiryStatus addUserMessage(LocalDateTime now) {
        requireNotClosed();
        InquiryStatus previous = status;
        lastPublicActivityAt = now;
        if (status == InquiryStatus.RESOLVED) {
            status = InquiryStatus.NEW;
            staffActionSince = now;
            resolvedAt = null;
        }
        return previous;
    }

    public void withdraw(Long actorUserId, LocalDateTime now) {
        requireStatus(InquiryStatus.NEW);
        close(actorUserId, InquiryClosureReason.WITHDRAWN, now);
    }

    public void closeByUser(Long actorUserId, LocalDateTime now) {
        requireStatus(InquiryStatus.RESOLVED);
        close(actorUserId, InquiryClosureReason.USER_CONFIRMED, now);
    }

    public InquiryStatus closeByAdmin(Long actorUserId, LocalDateTime now) {
        requireNotClosed();
        InquiryStatus previous = status;
        close(actorUserId, InquiryClosureReason.ADMIN_CLOSED, now);
        return previous;
    }

    public InquiryStatus autoClose(LocalDateTime now) {
        requireStatus(InquiryStatus.RESOLVED);
        InquiryStatus previous = status;
        close(null, InquiryClosureReason.AUTO_CLOSED, now);
        return previous;
    }

    public InquiryStatus reopenByAdmin(LocalDateTime now) {
        requireStatus(InquiryStatus.CLOSED);
        InquiryStatus previous = status;
        status = InquiryStatus.IN_PROGRESS;
        staffActionSince = now;
        resolvedAt = null;
        clearClosure();
        return previous;
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && userId.equals(authorUserId);
    }

    private void close(Long actorUserId, InquiryClosureReason reason, LocalDateTime now) {
        status = InquiryStatus.CLOSED;
        staffActionSince = null;
        closedAt = now;
        closedByUserId = actorUserId;
        closureReason = reason;
    }

    private void clearClosure() {
        closedAt = null;
        closedByUserId = null;
        closureReason = null;
    }

    private void requireStatus(InquiryStatus expected) {
        if (status != expected) throw new BusinessException(ErrorCode.INVALID_INQUIRY_STATE);
    }

    private void requireNotClosed() {
        if (status == InquiryStatus.CLOSED) throw new BusinessException(ErrorCode.INVALID_INQUIRY_STATE);
    }
}
