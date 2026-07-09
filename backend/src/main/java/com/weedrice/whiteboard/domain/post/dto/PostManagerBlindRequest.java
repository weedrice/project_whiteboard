package com.weedrice.whiteboard.domain.post.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class PostManagerBlindRequest {
    @Size(max = 50)
    private String reason;
}
