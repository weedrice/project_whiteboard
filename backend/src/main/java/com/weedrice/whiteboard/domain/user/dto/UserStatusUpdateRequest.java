package com.weedrice.whiteboard.domain.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserStatusUpdateRequest {
    private String status; // ACTIVE, SUSPENDED
}
