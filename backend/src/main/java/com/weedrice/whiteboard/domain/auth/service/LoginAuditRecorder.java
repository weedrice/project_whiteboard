package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.LoginRequest;
import com.weedrice.whiteboard.domain.user.entity.User;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LoginAuditRecorder {

    private final LoginHistoryAuditService loginHistoryAuditService;
    private final Counter loginSuccessAuditFailures;
    private final Counter loginFailureAuditFailures;

    public LoginAuditRecorder(LoginHistoryAuditService loginHistoryAuditService, MeterRegistry meterRegistry) {
        this.loginHistoryAuditService = loginHistoryAuditService;
        this.loginSuccessAuditFailures = meterRegistry.counter(
                "noviis.login.audit.failures", "audit_type", "login_success");
        this.loginFailureAuditFailures = meterRegistry.counter(
                "noviis.login.audit.failures", "audit_type", "login_failure");
    }

    public void recordSuccess(LoginRequest request, User user, LoginClientMetadata metadata) {
        try {
            loginHistoryAuditService.recordSuccess(
                    user.getUserId(),
                    request.getLoginId(),
                    metadata.ipAddress(),
                    metadata.userAgent());
        } catch (RuntimeException exception) {
            loginSuccessAuditFailures.increment();
            log.warn("Failed to record login success history. failureType={}",
                    exception.getClass().getSimpleName());
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
            loginFailureAuditFailures.increment();
            log.warn("Failed to record login failure history. failureType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
