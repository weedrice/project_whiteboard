package com.weedrice.whiteboard.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommentTopicSubscriptionRequest {

    @NotBlank
    @Size(max = 80)
    private String subscriberId;
}
