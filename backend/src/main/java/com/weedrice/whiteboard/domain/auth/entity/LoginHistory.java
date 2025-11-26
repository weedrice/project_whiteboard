package com.weedrice.whiteboard.domain.auth.entity;

import com.weedrice.whiteboard.domain.common.BaseTimeEntity;
import com.weedrice.whiteboard.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "login_histories",
        indexes = {
                @Index(name = "idx_login_histories_user", columnList = "user_id, created_at"),
                @Index(name = "idx_login_histories_ip", columnList = "ip_address, created_at"),
                @Index(name = "idx_login_histories_login_id", columnList = "login_id, created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "login_id", nullable = false, length = 30)
    private String loginId;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "is_success", nullable = false, length = 1)
    private String isSuccess; // Y, N

    @Column(name = "failure_reason", length = 50)
    private String failureReason;

    @Builder
    public LoginHistory(User user, String loginId, String ipAddress, String userAgent,
                        boolean isSuccess, String failureReason) {
        this.user = user;
        this.loginId = loginId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.isSuccess = isSuccess ? "Y" : "N";
        this.failureReason = failureReason;
    }

    public static LoginHistory success(User user, String loginId, String ipAddress, String userAgent) {
        return LoginHistory.builder()
                .user(user)
                .loginId(loginId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .isSuccess(true)
                .build();
    }

    public static LoginHistory failure(String loginId, String ipAddress, String userAgent, String reason) {
        return LoginHistory.builder()
                .loginId(loginId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .isSuccess(false)
                .failureReason(reason)
                .build();
    }
}