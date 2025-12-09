package com.weedrice.whiteboard.domain.post.dto;

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
    private Long durationMs; // 추가된 체류 시간
}
