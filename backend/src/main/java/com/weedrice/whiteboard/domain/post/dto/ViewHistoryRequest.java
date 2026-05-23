package com.weedrice.whiteboard.domain.post.dto;

import jakarta.validation.constraints.Max;
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
    public static final long MAX_DURATION_MS = 86_400_000L;

    private Long lastReadCommentId;
    @PositiveOrZero
    @Max(MAX_DURATION_MS)
    private Long durationMs; // 추가된 체류 시간
}
