package com.weedrice.whiteboard.domain.sanction.entity;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "sanctions", indexes = {
        @Index(name = "idx_sanctions_user", columnList = "target_user_id, end_date"),
        @Index(name = "idx_sanctions_admin", columnList = "admin_id")
})
public class Sanction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sanction_id")
    private Long sanctionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

    @Column(name = "type", length = 50, nullable = false)
    private String type; // WARNING, MUTE, BAN

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "content_id")
    private Long contentId;

    @Column(name = "content_type", length = 50)
    private String contentType;

    @Builder
    public Sanction(User targetUser, Admin admin, String type, String remark, LocalDateTime startDate, LocalDateTime endDate, Long contentId, String contentType) {
        this.targetUser = targetUser;
        this.admin = admin;
        this.type = type;
        this.remark = remark;
        this.startDate = startDate;
        this.endDate = endDate;
        this.contentId = contentId;
        this.contentType = contentType;
    }
}
