package com.weedrice.whiteboard.domain.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @Size(min = 2, max = 50, message = "표시 이름은 2-50자여야 합니다")
    private String displayName;

    private String profileImageUrl;
    
    private Long profileImageId;
}
