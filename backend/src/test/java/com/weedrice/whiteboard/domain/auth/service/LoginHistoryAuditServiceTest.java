package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginHistoryAuditServiceTest {

    @Mock
    private LoginHistoryRepository loginHistoryRepository;
    @Mock
    private EntityManager entityManager;

    @Test
    void recordSuccess_normalizesClientMetadata() {
        LoginHistoryAuditService service = new LoginHistoryAuditService(loginHistoryRepository, entityManager);
        User user = User.builder().loginId("testuser").build();
        String longIpAddress = "1".repeat(60);
        String longUserAgent = "a".repeat(600);
        when(entityManager.getReference(User.class, 1L)).thenReturn(user);

        service.recordSuccess(1L, "testuser", longIpAddress, longUserAgent);

        verify(loginHistoryRepository).save(argThat(history ->
                history.getUser() == user
                        && "testuser".equals(history.getLoginId())
                        && longIpAddress.substring(0, 45).equals(history.getIpAddress())
                        && longUserAgent.substring(0, 500).equals(history.getUserAgent())
                        && Boolean.TRUE.equals(history.getIsSuccess())));
    }

    @Test
    void recordFailure_defaultsBlankIpAndNormalizesUserAgent() {
        LoginHistoryAuditService service = new LoginHistoryAuditService(loginHistoryRepository, entityManager);
        String longUserAgent = "a".repeat(600);

        service.recordFailure("testuser", "  ", longUserAgent, "AUTHENTICATION_FAILED");

        verify(loginHistoryRepository).save(argThat(history ->
                "testuser".equals(history.getLoginId())
                        && LoginClientMetadataNormalizer.UNKNOWN_IP_ADDRESS.equals(history.getIpAddress())
                        && longUserAgent.substring(0, 500).equals(history.getUserAgent())
                        && Boolean.FALSE.equals(history.getIsSuccess())
                        && "AUTHENTICATION_FAILED".equals(history.getFailureReason())));
    }

    @Test
    void recordFailure_trimsAndTruncatesLoginId() {
        LoginHistoryAuditService service = new LoginHistoryAuditService(loginHistoryRepository, entityManager);
        String loginId = "  " + "a".repeat(40) + "  ";

        service.recordFailure(loginId, "127.0.0.1", "agent", "AUTHENTICATION_FAILED");

        verify(loginHistoryRepository).save(argThat(history ->
                "a".repeat(30).equals(history.getLoginId())
                        && Boolean.FALSE.equals(history.getIsSuccess())));
    }

    @Test
    void recordFailure_defaultsBlankLoginId() {
        LoginHistoryAuditService service = new LoginHistoryAuditService(loginHistoryRepository, entityManager);

        service.recordFailure("  ", "127.0.0.1", "agent", "AUTHENTICATION_FAILED");

        verify(loginHistoryRepository).save(argThat(history ->
                LoginClientMetadataNormalizer.UNKNOWN_LOGIN_ID.equals(history.getLoginId())
                        && Boolean.FALSE.equals(history.getIsSuccess())));
    }
}
