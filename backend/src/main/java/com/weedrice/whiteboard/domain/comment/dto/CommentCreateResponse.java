package com.weedrice.whiteboard.domain.comment.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommentCreateResponse {
    private Long commentId;
    private Integer earnedPoints;
}
