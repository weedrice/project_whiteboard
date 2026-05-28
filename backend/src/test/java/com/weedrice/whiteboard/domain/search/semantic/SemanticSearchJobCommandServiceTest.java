package com.weedrice.whiteboard.domain.search.semantic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SemanticSearchJobCommandServiceTest {

    private SemanticSearchJobRepository jobRepository;
    private SemanticSearchJobCommandService commandService;

    @BeforeEach
    void setUp() {
        jobRepository = mock(SemanticSearchJobRepository.class);
        commandService = new SemanticSearchJobCommandService(jobRepository);
    }

    @Test
    void commandMethodsUseRequiresNewTransactions() throws NoSuchMethodException {
        assertRequiresNew("recoverStaleProcessingJobs",
                LocalDateTime.class, int.class, String.class);
        assertRequiresNew("claimForProcessing",
                Long.class, int.class, LocalDateTime.class);
        assertRequiresNew("findClaimed",
                Long.class, LocalDateTime.class);
        assertRequiresNew("markCompleted",
                Long.class, LocalDateTime.class, LocalDateTime.class);
        assertRequiresNew("markFailedIfCurrent",
                Long.class, LocalDateTime.class, int.class, String.class);
    }

    @Test
    void findClaimed_usesReadOnlyTransaction() throws NoSuchMethodException {
        Method method = SemanticSearchJobCommandService.class.getMethod(
                "findClaimed", Long.class, LocalDateTime.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    void recoverStaleProcessingJobs_delegatesToRepository() {
        LocalDateTime staleBefore = LocalDateTime.of(2026, 5, 19, 14, 50);
        when(jobRepository.recoverStaleProcessingJobs(staleBefore, 3, "lease expired")).thenReturn(2);

        int recovered = commandService.recoverStaleProcessingJobs(staleBefore, 3, "lease expired");

        assertThat(recovered).isEqualTo(2);
        verify(jobRepository).recoverStaleProcessingJobs(staleBefore, 3, "lease expired");
    }

    @Test
    void claimForProcessing_delegatesToRepository() {
        LocalDateTime claimedAt = LocalDateTime.of(2026, 5, 19, 15, 0);
        when(jobRepository.claimForProcessing(1L, 3, claimedAt)).thenReturn(1);

        int claimed = commandService.claimForProcessing(1L, 3, claimedAt);

        assertThat(claimed).isEqualTo(1);
        verify(jobRepository).claimForProcessing(1L, 3, claimedAt);
    }

    @Test
    void findClaimed_delegatesToRepository() {
        LocalDateTime claimedAt = LocalDateTime.of(2026, 5, 19, 15, 0);
        SemanticSearchJob job = new SemanticSearchJob(1L, "POST", 10L, "UPSERT", claimedAt);
        when(jobRepository.findClaimed(1L, claimedAt)).thenReturn(Optional.of(job));

        Optional<SemanticSearchJob> found = commandService.findClaimed(1L, claimedAt);

        assertThat(found).contains(job);
        verify(jobRepository).findClaimed(1L, claimedAt);
    }

    @Test
    void markCompleted_delegatesToRepository() {
        LocalDateTime claimedAt = LocalDateTime.of(2026, 5, 19, 15, 0);
        LocalDateTime completedAt = LocalDateTime.of(2026, 5, 19, 15, 1);
        when(jobRepository.markCompleted(1L, claimedAt, completedAt)).thenReturn(1);

        int completed = commandService.markCompleted(1L, claimedAt, completedAt);

        assertThat(completed).isEqualTo(1);
        verify(jobRepository).markCompleted(1L, claimedAt, completedAt);
    }

    @Test
    void markFailedIfCurrent_delegatesToRepository() {
        LocalDateTime claimedAt = LocalDateTime.of(2026, 5, 19, 15, 0);
        when(jobRepository.markFailedIfCurrent(1L, claimedAt, 3, "provider down")).thenReturn(1);

        int failed = commandService.markFailedIfCurrent(1L, claimedAt, 3, "provider down");

        assertThat(failed).isEqualTo(1);
        verify(jobRepository).markFailedIfCurrent(1L, claimedAt, 3, "provider down");
    }

    private void assertRequiresNew(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = SemanticSearchJobCommandService.class.getMethod(methodName, parameterTypes);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
