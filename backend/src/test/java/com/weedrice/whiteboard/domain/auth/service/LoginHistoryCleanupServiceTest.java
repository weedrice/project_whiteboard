package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.LoginHistoryRetentionProperties;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginHistoryCleanupServiceTest {

    @Mock private LoginHistoryRepository loginHistoryRepository;

    @Test
    void deleteExpiredBatch_usesConfiguredRetentionAndBatchSize() {
        LoginHistoryRetentionProperties properties = new LoginHistoryRetentionProperties();
        properties.setRetentionDays(90);
        properties.setCleanupBatchSize(250);
        Clock clock = Clock.fixed(Instant.parse("2026-07-19T03:00:00Z"), ZoneOffset.UTC);
        LocalDateTime expectedCutoff = LocalDateTime.of(2026, 4, 20, 3, 0);
        when(loginHistoryRepository.deleteCreatedBeforeBatch(expectedCutoff, 250)).thenReturn(17);

        LoginHistoryCleanupService service = new LoginHistoryCleanupService(
                loginHistoryRepository, properties, clock);

        assertThat(service.deleteExpiredBatch()).isEqualTo(17);
        verify(loginHistoryRepository).deleteCreatedBeforeBatch(expectedCutoff, 250);
    }
}
