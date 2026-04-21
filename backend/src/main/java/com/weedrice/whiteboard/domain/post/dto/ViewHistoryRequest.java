package com.weedrice.whiteboard.domain.post.dto;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViewHistoryRequest {
    private Long lastReadCommentId;
    @PositiveOrZero
    private Long durationMs; // 추가된 체류 시간
}
