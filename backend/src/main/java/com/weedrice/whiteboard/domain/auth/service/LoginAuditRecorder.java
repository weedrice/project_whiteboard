package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.LoginRequest;
import com.weedrice.whiteboard.domain.user.entity.User;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    public void recordSuccessAfterCommit(LoginRequest request, User user, LoginClientMetadata metadata) {
        Long userId = user.getUserId();
        String loginId = request.getLoginId();
        String ipAddress = metadata.ipAddress();
        String userAgent = metadata.userAgent();
        Runnable auditTask = () -> recordSuccess(
                userId,
                loginId,
                ipAddress,
                userAgent);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            auditTask.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                auditTask.run();
            }
        });
    }

    private void recordSuccess(Long userId, String loginId, String ipAddress, String userAgent) {
        try {
            loginHistoryAuditService.recordSuccess(
                    userId,
                    loginId,
                    ipAddress,
                    userAgent);
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
