package com.weedrice.whiteboard.domain.board.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BoardRecentUpdateResponse {
    private String boardUrl;
    private LocalDateTime latestPostAt;
}
