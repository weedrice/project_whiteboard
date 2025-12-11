package com.weedrice.whiteboard.domain.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @Size(min = 2, max = 50, message = "{validation.user.displayName.size}")
    private String displayName;

    private String profileImageUrl;
    
    private Long profileImageId;
}
