package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.LoginRequest;
import com.weedrice.whiteboard.domain.user.entity.User;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoginAuditRecorderTest {

    @Mock
    private LoginHistoryAuditService loginHistoryAuditService;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void recordSuccessAfterCommit_defersAuditUntilTransactionCommit() {
        LoginAuditRecorder recorder = new LoginAuditRecorder(
                loginHistoryAuditService,
                new SimpleMeterRegistry());
        LoginRequest request = new LoginRequest("testuser", "password");
        User user = User.builder().loginId("testuser").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        LoginClientMetadata metadata = new LoginClientMetadata("127.0.0.1", "test-agent");
        TransactionSynchronizationManager.initSynchronization();

        recorder.recordSuccessAfterCommit(request, user, metadata);

        verify(loginHistoryAuditService, never()).recordSuccess(
                1L, "testuser", "127.0.0.1", "test-agent");
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        verify(loginHistoryAuditService).recordSuccess(
                1L, "testuser", "127.0.0.1", "test-agent");
    }

    @Test
    void recordSuccessAfterCommit_withoutTransaction_recordsImmediately() {
        LoginAuditRecorder recorder = new LoginAuditRecorder(
                loginHistoryAuditService,
                new SimpleMeterRegistry());
        LoginRequest request = new LoginRequest("testuser", "password");
        User user = User.builder().loginId("testuser").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        recorder.recordSuccessAfterCommit(request, user, LoginClientMetadata.empty());

        verify(loginHistoryAuditService).recordSuccess(1L, "testuser", null, null);
    }

    @Test
    void recordFailure_withActiveTransactionSynchronization_recordsImmediately() {
        LoginAuditRecorder recorder = new LoginAuditRecorder(
                loginHistoryAuditService,
                new SimpleMeterRegistry());
        LoginRequest request = new LoginRequest("testuser", "password");
        LoginClientMetadata metadata = new LoginClientMetadata("127.0.0.1", "test-agent");
        TransactionSynchronizationManager.initSynchronization();

        recorder.recordFailure(request, metadata, "AUTHENTICATION_FAILED");

        verify(loginHistoryAuditService).recordFailure(
                "testuser", "127.0.0.1", "test-agent", "AUTHENTICATION_FAILED");
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }
}
