package com.weedrice.whiteboard.domain.badge.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RepresentativeBadgeRequest {
    @Size(max = 50)
    private String badgeCode;
}
