package com.weedrice.whiteboard.domain.user.dto;

import com.weedrice.whiteboard.global.validation.NoHtml;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @Size(min = 2, max = 50, message = "{validation.user.displayName.size}")
    @NoHtml
    private String displayName;

    private String profileImageUrl;
    
    private Long profileImageId;
}
