package com.weedrice.whiteboard.domain.user.dto;

import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserAdminResponse {
    private Long userId;
    private String loginId;
    private String email;
    private String displayName;
    private String profileImageUrl;
    private String status;
    private String isEmailVerified;
    private String isSuperAdmin;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    public static UserAdminResponse from(User user) {
        return UserAdminResponse.builder()
                .userId(user.getUserId())
                .loginId(user.getLoginId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .profileImageUrl(user.getProfileImageUrl())
                .status(user.getStatus())
                .isEmailVerified(user.getIsEmailVerified())
                .isSuperAdmin(user.getIsSuperAdmin())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
