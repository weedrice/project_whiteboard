package com.weedrice.whiteboard.domain.admin.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "ip_blocks")
public class IpBlock extends BaseTimeEntity {

    @Id
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private Admin admin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processor_user_id", nullable = false)
    private User processorUser;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Builder
    public IpBlock(String ipAddress, Admin admin, User processorUser, String reason,
                   LocalDateTime startDate, LocalDateTime endDate) {
        this.ipAddress = ipAddress;
        this.admin = admin;
        this.processorUser = processorUser;
        this.reason = reason;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void reactivate(Admin admin, User processorUser, String reason,
                           LocalDateTime startDate, LocalDateTime endDate) {
        this.admin = admin;
        this.processorUser = processorUser;
        this.reason = reason;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void expire(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public boolean isActiveAt(LocalDateTime now) {
        return endDate == null || endDate.isAfter(now);
    }
}
