package com.weedrice.whiteboard.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class MyInfoResponse {
    private Long userId;
    private String loginId;
    private String email;
    private String displayName;
    private String profileImageUrl;
    private String status;
    private String role;
    private boolean isEmailVerified;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt; // Added field
}
