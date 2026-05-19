package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.LoginRequest;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginAuditRecorder {

    private final LoginHistoryAuditService loginHistoryAuditService;

    public void recordSuccess(LoginRequest request, User user, LoginClientMetadata metadata) {
        try {
            loginHistoryAuditService.recordSuccess(
                    user.getUserId(),
                    request.getLoginId(),
                    metadata.ipAddress(),
                    metadata.userAgent());
        } catch (RuntimeException exception) {
            log.warn("Failed to record login success history", exception);
        }
    }

    public void recordFailure(LoginRequest request, LoginClientMetadata metadata, String failureReason) {
        try {
            loginHistoryAuditService.recordFailure(
                    request.getLoginId(),
                    metadata.ipAddress(),
                    metadata.userAgent(),
                    failureReason);
        } catch (RuntimeException exception) {
            log.warn("Failed to record login failure history", exception);
        }
    }
}
