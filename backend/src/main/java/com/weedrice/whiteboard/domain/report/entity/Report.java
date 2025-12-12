package com.weedrice.whiteboard.domain.report.entity;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "reports", uniqueConstraints = {
        @UniqueConstraint(name = "uk_reports_user_target", columnNames = {"reporter_id", "target_type", "target_id"})
}, indexes = {
        @Index(name = "idx_reports_status", columnList = "status, created_at DESC"),
        @Index(name = "idx_reports_reporter", columnList = "reporter_id"),
        @Index(name = "idx_reports_target", columnList = "target_type, target_id")
})
public class Report extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Column(name = "target_type", length = 50, nullable = false)
    private String targetType; // POST, COMMENT, USER

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "reason_type", length = 50, nullable = false)
    private String reasonType; // SPAM, ABUSE, ADULT 등 (공통코드)

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "status", length = 50, nullable = false)
    private String status; // PENDING, RESOLVED, REJECTED

    @Column(name = "contents", columnDefinition = "TEXT")
    private String contents;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private Admin admin; // 처리 관리자

    @Builder
    public Report(User reporter, String targetType, Long targetId, String reasonType, String remark, String contents) {
        this.reporter = reporter;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reasonType = reasonType;
        this.remark = remark;
        this.contents = contents;
        this.status = "PENDING";
    }

    public void processReport(Admin admin, String status, String remark) {
        this.admin = admin;
        this.status = status;
        this.remark = remark;
    }
}
