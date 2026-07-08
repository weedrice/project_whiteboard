package com.weedrice.whiteboard.domain.post.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PostCreateResponse {
    private Long postId;
    private Integer earnedPoints;
}
