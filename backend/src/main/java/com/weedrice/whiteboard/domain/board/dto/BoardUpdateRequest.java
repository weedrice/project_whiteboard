package com.weedrice.whiteboard.domain.board.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BoardUpdateRequest {
    @Size(max = 255)
    private String description;

    private String iconUrl;
    private String bannerUrl;
    private Integer sortOrder;
    private Boolean allowNsfw;
}
