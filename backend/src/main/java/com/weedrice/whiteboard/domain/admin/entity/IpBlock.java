package com.weedrice.whiteboard.domain.admin.entity;

import com.weedrice.whiteboard.global.common.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
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
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Builder
    public IpBlock(String ipAddress, Admin admin, String reason, LocalDateTime startDate, LocalDateTime endDate) {
        this.ipAddress = ipAddress;
        this.admin = admin;
        this.reason = reason;
        this.startDate = startDate;
        this.endDate = endDate;
    }
}
