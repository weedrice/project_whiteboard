package com.weedrice.whiteboard.domain.admin.dto;

import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SuperAdminResponse {
    private String loginId;
    private boolean isSuperAdmin;

    public static SuperAdminResponse from(User user) {
        return SuperAdminResponse.builder()
                .loginId(user.getLoginId())
                .isSuperAdmin("Y".equals(user.getIsSuperAdmin()))
                .build();
    }
}
