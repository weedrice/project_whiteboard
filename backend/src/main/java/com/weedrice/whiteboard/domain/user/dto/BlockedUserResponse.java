package com.weedrice.whiteboard.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class BlockedUserResponse {
    private Long userId;
    private String loginId;
    private String displayName;
    private LocalDateTime blockedAt;
    private boolean withdrawn;

    public BlockedUserResponse(Long userId, String loginId, String displayName, LocalDateTime blockedAt) {
        this(userId, loginId, displayName, blockedAt, false);
    }
}
