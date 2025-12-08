package com.weedrice.whiteboard.domain.admin.dto;

import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SuperAdminUpdateResponse {
    private String loginId;
    private boolean isSuperAdmin;

    public static SuperAdminUpdateResponse from(User user) {
        return SuperAdminUpdateResponse.builder()
                .loginId(user.getLoginId())
                .isSuperAdmin(user.getIsSuperAdmin())
                .build();
    }
}
