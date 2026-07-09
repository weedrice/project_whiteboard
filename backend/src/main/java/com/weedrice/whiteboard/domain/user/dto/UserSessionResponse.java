package com.weedrice.whiteboard.domain.user.dto;

import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserSessionResponse {
    private Long sessionId;
    private String deviceSummary;
    private String ipAddress;
    private LocalDateTime lastUsedAt;
    private LocalDateTime expiresAt;
    private boolean current;

    public static UserSessionResponse from(RefreshToken refreshToken, boolean current) {
        return UserSessionResponse.builder()
                .sessionId(refreshToken.getTokenId())
                .deviceSummary(SessionDeviceSummary.from(refreshToken.getDeviceInfo()))
                .ipAddress(SessionIpMasker.mask(refreshToken.getIpAddress()))
                .lastUsedAt(refreshToken.getCreatedAt())
                .expiresAt(refreshToken.getExpiresAt())
                .current(current)
                .build();
    }
}
