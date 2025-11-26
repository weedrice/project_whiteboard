package com.weedrice.whiteboard.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UpdateProfileResponse {
    private Long userId;
    private String displayName;
    private String profileImageUrl;
}
