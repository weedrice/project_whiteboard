package com.weedrice.whiteboard.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RefreshResponse {
    private String accessToken;
    private long expiresIn;
}
