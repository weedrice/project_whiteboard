package com.weedrice.whiteboard.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RefreshResponse {
    private String accessToken;
    private String refreshToken; // Added for refresh token rotation
    private long expiresIn;
}
