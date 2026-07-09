package com.weedrice.whiteboard.domain.user.dto;

import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class LoginHistoryResponse {
    private Long historyId;
    private String loginId;
    private String ipAddress;
    private String deviceSummary;
    private Boolean success;
    private String failureReason;
    private LocalDateTime createdAt;

    public static LoginHistoryResponse from(LoginHistory history) {
        return LoginHistoryResponse.builder()
                .historyId(history.getHistoryId())
                .loginId(history.getLoginId())
                .ipAddress(SessionIpMasker.mask(history.getIpAddress()))
                .deviceSummary(SessionDeviceSummary.from(history.getUserAgent()))
                .success(history.getIsSuccess())
                .failureReason(history.getFailureReason())
                .createdAt(history.getCreatedAt())
                .build();
    }
}
