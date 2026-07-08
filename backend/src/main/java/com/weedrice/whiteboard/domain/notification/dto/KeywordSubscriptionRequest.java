package com.weedrice.whiteboard.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KeywordSubscriptionRequest {
    @NotBlank
    @Size(max = 50)
    private String keyword;
}
