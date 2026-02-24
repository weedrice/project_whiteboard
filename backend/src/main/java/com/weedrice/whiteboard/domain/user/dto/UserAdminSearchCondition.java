package com.weedrice.whiteboard.domain.user.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserAdminSearchCondition {
    private String status;
    private String role;
    private Boolean isEmailVerified;
    private Boolean isSuperAdmin;
    private Boolean isWithdrawn;
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
    private LocalDateTime lastLoginFrom;
    private LocalDateTime lastLoginTo;
    private Long minActivityCount;
}
