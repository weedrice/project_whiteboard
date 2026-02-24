package com.weedrice.whiteboard.domain.user.dto;

import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminUserDetailResponse {
    private Long userId;
    private String loginId;
    private String email;
    private String displayName;
    private String profileImageUrl;
    private String status;
    private Boolean isEmailVerified;
    private Boolean isSuperAdmin;
    private String role;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private LocalDateTime deletedAt;
    private long postCount;
    private long commentCount;
    private long subscriptionCount;
    private RecentLogin recentLogin;
    private SanctionSummary sanctionSummary;
    private ReportSummary reportSummary;

    @Getter
    @Builder
    public static class RecentLogin {
        private String ipAddress;
        private String userAgent;
        private LocalDateTime loggedAt;
    }

    @Getter
    @Builder
    public static class SanctionSummary {
        private long count;
        private String recentType;
        private String recentRemark;
        private LocalDateTime recentStartDate;
        private LocalDateTime recentEndDate;
    }

    @Getter
    @Builder
    public static class ReportSummary {
        private long totalCount;
        private long pendingCount;
    }

    public static AdminUserDetailResponse from(
            User user,
            String role,
            long postCount,
            long commentCount,
            long subscriptionCount,
            LoginHistory recentLogin,
            long sanctionCount,
            Sanction recentSanction,
            long reportTotalCount,
            long reportPendingCount) {
        return AdminUserDetailResponse.builder()
                .userId(user.getUserId())
                .loginId(user.getLoginId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .profileImageUrl(user.getProfileImageUrl())
                .status(user.getStatus())
                .isEmailVerified(user.getIsEmailVerified())
                .isSuperAdmin(user.getIsSuperAdmin())
                .role(role)
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .deletedAt(user.getDeletedAt())
                .postCount(postCount)
                .commentCount(commentCount)
                .subscriptionCount(subscriptionCount)
                .recentLogin(recentLogin != null
                        ? RecentLogin.builder()
                                .ipAddress(recentLogin.getIpAddress())
                                .userAgent(recentLogin.getUserAgent())
                                .loggedAt(recentLogin.getCreatedAt())
                                .build()
                        : null)
                .sanctionSummary(SanctionSummary.builder()
                        .count(sanctionCount)
                        .recentType(recentSanction != null ? recentSanction.getType() : null)
                        .recentRemark(recentSanction != null ? recentSanction.getRemark() : null)
                        .recentStartDate(recentSanction != null ? recentSanction.getStartDate() : null)
                        .recentEndDate(recentSanction != null ? recentSanction.getEndDate() : null)
                        .build())
                .reportSummary(ReportSummary.builder()
                        .totalCount(reportTotalCount)
                        .pendingCount(reportPendingCount)
                        .build())
                .build();
    }
}
