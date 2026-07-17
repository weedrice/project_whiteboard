package com.weedrice.whiteboard.domain.search.semantic;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SemanticSearchReindexRepositoryTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final SemanticSearchReindexRepository repository =
            new SemanticSearchReindexRepository(jdbcTemplate);

    @Test
    void claimUsesDueTimeAndLeaseTokenAsCasBoundary() {
        UUID leaseToken = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 7, 17, 12, 0);

        repository.claim(7L, leaseToken, now);

        verify(jdbcTemplate).update(
                contains("next_attempt_at <= ?"), eq(now), eq(leaseToken), eq(now), eq(7L), eq(now));
    }

    @Test
    void failureUpdateAddsRetryAndTransitionsToFailedAtLimit() {
        UUID leaseToken = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 7, 17, 12, 0);

        repository.markFailed(7L, leaseToken, 5, now.plusMinutes(4), now, "bounded error");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(Object[].class));
        assertThat(sql.getValue())
                .contains("retry_count=LEAST(retry_count + 1, ?)")
                .contains("THEN 'FAILED' ELSE 'PENDING'")
                .contains("lease_token=?");
    }

    @Test
    void redriveOnlyUpdatesFailedJobAndPreservesCursorColumns() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 17, 12, 0);

        repository.redrive(7L, now);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(Object[].class));
        assertThat(sql.getValue())
                .contains("WHERE reindex_job_id=? AND status='FAILED'")
                .doesNotContain("last_content_id=")
                .doesNotContain("content_phase=");
    }
}
