package com.weedrice.whiteboard.domain.admin.dto;

import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class SuperAdminResponse {
    private Long userId;
    private String loginId;
    private String displayName;
    private boolean isSuperAdmin;
    private LocalDateTime createdAt;

    public static SuperAdminResponse from(User user) {
        return SuperAdminResponse.builder()
                .userId(user.getUserId())
                .loginId(user.getLoginId())
                .displayName(user.getDisplayName())
                .isSuperAdmin("Y".equals(user.getIsSuperAdmin()))
                .createdAt(user.getCreatedAt())
                .build();
    }
}
