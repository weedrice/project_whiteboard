package com.weedrice.whiteboard.domain.feed.repository;

import com.weedrice.whiteboard.domain.feed.FeedGenerationJobPolicy;
import com.weedrice.whiteboard.domain.feed.entity.FeedGenerationJob;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class FeedGenerationJobRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FeedGenerationJobRepository feedGenerationJobRepository;

    @Test
    void insertIgnoreIsIdempotentForTheSamePost() {
        assertThat(feedGenerationJobRepository.insertIgnore(100L, 10L)).isEqualTo(1);
        assertThat(feedGenerationJobRepository.insertIgnore(100L, 10L)).isZero();

        assertThat(feedGenerationJobRepository.count()).isEqualTo(1);
        FeedGenerationJob stored = feedGenerationJobRepository.findAll().getFirst();
        assertThat(stored.getPostId()).isEqualTo(100L);
        assertThat(stored.getBoardId()).isEqualTo(10L);
        assertThat(stored.getStatus()).isEqualTo(FeedGenerationJob.STATUS_PENDING);
        assertThat(stored.getRetryCount()).isZero();
    }

    @Test
    void claimForProcessing_setsProcessingLeaseForPendingJob() {
        FeedGenerationJob job = persistJob(100L, 10L);
        LocalDateTime claimedAt = LocalDateTime.of(2026, 5, 11, 16, 30);

        int updated = feedGenerationJobRepository.claimForProcessing(
                job.getJobId(),
                FeedGenerationJobPolicy.MAX_RETRY_COUNT,
                claimedAt);

        entityManager.flush();
        entityManager.clear();

        FeedGenerationJob claimed = entityManager.find(FeedGenerationJob.class, job.getJobId());
        assertThat(updated).isEqualTo(1);
        assertThat(claimed.getStatus()).isEqualTo(FeedGenerationJob.STATUS_PROCESSING);
        assertThat(claimed.getProcessingStartedAt()).isEqualTo(claimedAt);
    }

    @Test
    void claimForProcessingByPostId_claimsMatchingPendingJob() {
        FeedGenerationJob job = persistJob(100L, 10L);
        LocalDateTime claimedAt = LocalDateTime.of(2026, 5, 11, 16, 30);

        int updated = feedGenerationJobRepository.claimForProcessingByPostId(
                100L,
                FeedGenerationJobPolicy.MAX_RETRY_COUNT,
                claimedAt);

        entityManager.flush();
        entityManager.clear();

        FeedGenerationJob claimed = entityManager.find(FeedGenerationJob.class, job.getJobId());
        assertThat(updated).isEqualTo(1);
        assertThat(claimed.getStatus()).isEqualTo(FeedGenerationJob.STATUS_PROCESSING);
        assertThat(claimed.getProcessingStartedAt()).isEqualTo(claimedAt);
    }

    @Test
    void markCompleted_completesProcessingJob() {
        LocalDateTime claimedAt = LocalDateTime.of(2026, 5, 11, 16, 30);
        FeedGenerationJob job = persistProcessingJob(100L, 10L, claimedAt, 0);
        LocalDateTime completedAt = LocalDateTime.of(2026, 5, 11, 16, 31);

        int updated = feedGenerationJobRepository.markCompleted(
                job.getJobId(),
                claimedAt,
                completedAt);

        entityManager.flush();
        entityManager.clear();

        FeedGenerationJob completed = entityManager.find(FeedGenerationJob.class, job.getJobId());
        assertThat(updated).isEqualTo(1);
        assertThat(completed.getStatus()).isEqualTo(FeedGenerationJob.STATUS_COMPLETED);
        assertThat(completed.getProcessingStartedAt()).isNull();
        assertThat(completed.getCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    void markCompleted_skipsProcessingJobWithDifferentLease() {
        LocalDateTime claimedAt = LocalDateTime.of(2026, 5, 11, 16, 30);
        FeedGenerationJob job = persistProcessingJob(100L, 10L, claimedAt, 0);
        LocalDateTime staleClaimedAt = LocalDateTime.of(2026, 5, 11, 16, 20);
        LocalDateTime completedAt = LocalDateTime.of(2026, 5, 11, 16, 31);

        int updated = feedGenerationJobRepository.markCompleted(
                job.getJobId(),
                staleClaimedAt,
                completedAt);

        entityManager.flush();
        entityManager.clear();

        FeedGenerationJob processing = entityManager.find(FeedGenerationJob.class, job.getJobId());
        assertThat(updated).isZero();
        assertThat(processing.getStatus()).isEqualTo(FeedGenerationJob.STATUS_PROCESSING);
        assertThat(processing.getProcessingStartedAt()).isEqualTo(claimedAt);
        assertThat(processing.getCompletedAt()).isNull();
    }

    @Test
    void markCompleted_skipsRequeuedJobAfterExpiredLease() {
        LocalDateTime claimedAt = LocalDateTime.of(2026, 5, 11, 16, 30);
        FeedGenerationJob job = persistProcessingJob(100L, 10L, claimedAt, 1);
        int recovered = feedGenerationJobRepository.recoverStaleProcessingJobs(
                LocalDateTime.of(2026, 5, 11, 16, 35),
                FeedGenerationJobPolicy.MAX_RETRY_COUNT,
                LocalDateTime.of(2026, 5, 11, 16, 36),
                "Processing lease expired");
        entityManager.flush();
        entityManager.clear();

        LocalDateTime completedAt = LocalDateTime.of(2026, 5, 11, 16, 36);
        int completedCount = feedGenerationJobRepository.markCompleted(job.getJobId(), claimedAt, completedAt);

        entityManager.flush();
        entityManager.clear();

        FeedGenerationJob recoveredJob = entityManager.find(FeedGenerationJob.class, job.getJobId());
        assertThat(recovered).isEqualTo(1);
        assertThat(completedCount).isZero();
        assertThat(recoveredJob.getStatus()).isEqualTo(FeedGenerationJob.STATUS_PENDING);
        assertThat(recoveredJob.getProcessingStartedAt()).isNull();
        assertThat(recoveredJob.getCompletedAt()).isNull();
        assertThat(recoveredJob.getRetryCount()).isEqualTo(2);
    }

    @Test
    void markFailedIfCurrent_requeuesWithinRetryBudget() {
        LocalDateTime claimedAt = LocalDateTime.of(2026, 5, 11, 16, 30);
        FeedGenerationJob job = persistProcessingJob(100L, 10L, claimedAt, 3);

        int updated = feedGenerationJobRepository.markFailedIfCurrent(
                job.getJobId(),
                claimedAt,
                FeedGenerationJobPolicy.MAX_RETRY_COUNT,
                claimedAt.plusMinutes(8),
                "failure");

        entityManager.flush();
        entityManager.clear();

        FeedGenerationJob failed = entityManager.find(FeedGenerationJob.class, job.getJobId());
        assertThat(updated).isEqualTo(1);
        assertThat(failed.getStatus()).isEqualTo(FeedGenerationJob.STATUS_PENDING);
        assertThat(failed.getRetryCount()).isEqualTo(4);
        assertThat(failed.getProcessingStartedAt()).isNull();
        assertThat(failed.getLastErrorMessage()).isEqualTo("failure");
        assertThat(failed.getNextAttemptAt()).isEqualTo(claimedAt.plusMinutes(8));
    }

    @Test
    void markFailedIfCurrent_marksFailedAtRetryLimit() {
        LocalDateTime claimedAt = LocalDateTime.of(2026, 5, 11, 16, 30);
        FeedGenerationJob job = persistProcessingJob(100L, 10L, claimedAt, 4);

        int updated = feedGenerationJobRepository.markFailedIfCurrent(
                job.getJobId(),
                claimedAt,
                FeedGenerationJobPolicy.MAX_RETRY_COUNT,
                claimedAt.plusMinutes(16),
                "failure");

        entityManager.flush();
        entityManager.clear();

        FeedGenerationJob failed = entityManager.find(FeedGenerationJob.class, job.getJobId());
        assertThat(updated).isEqualTo(1);
        assertThat(failed.getStatus()).isEqualTo(FeedGenerationJob.STATUS_FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(FeedGenerationJobPolicy.MAX_RETRY_COUNT);
        assertThat(failed.getProcessingStartedAt()).isNull();
    }

    @Test
    void recoverStaleProcessingJobs_requeuesStaleJobs() {
        LocalDateTime staleStartedAt = LocalDateTime.of(2026, 5, 11, 16, 20);
        FeedGenerationJob job = persistProcessingJob(100L, 10L, staleStartedAt, 0);

        int updated = feedGenerationJobRepository.recoverStaleProcessingJobs(
                LocalDateTime.of(2026, 5, 11, 16, 25),
                FeedGenerationJobPolicy.MAX_RETRY_COUNT,
                LocalDateTime.of(2026, 5, 11, 16, 26),
                "Processing lease expired");

        entityManager.flush();
        entityManager.clear();

        FeedGenerationJob recovered = entityManager.find(FeedGenerationJob.class, job.getJobId());
        assertThat(updated).isEqualTo(1);
        assertThat(recovered.getStatus()).isEqualTo(FeedGenerationJob.STATUS_PENDING);
        assertThat(recovered.getRetryCount()).isEqualTo(1);
        assertThat(recovered.getProcessingStartedAt()).isNull();
        assertThat(recovered.getLastErrorMessage()).isEqualTo("Processing lease expired");
        assertThat(recovered.getNextAttemptAt()).isEqualTo(LocalDateTime.of(2026, 5, 11, 16, 26));
    }

    @Test
    void recoverStaleProcessingJobs_skipsJobsAtRetryLimit() {
        LocalDateTime staleStartedAt = LocalDateTime.of(2026, 5, 11, 16, 20);
        FeedGenerationJob job = persistProcessingJob(
                100L,
                10L,
                staleStartedAt,
                FeedGenerationJobPolicy.MAX_RETRY_COUNT);

        int updated = feedGenerationJobRepository.recoverStaleProcessingJobs(
                LocalDateTime.of(2026, 5, 11, 16, 25),
                FeedGenerationJobPolicy.MAX_RETRY_COUNT,
                LocalDateTime.of(2026, 5, 11, 16, 26),
                "Processing lease expired");

        entityManager.flush();
        entityManager.clear();

        FeedGenerationJob unchanged = entityManager.find(FeedGenerationJob.class, job.getJobId());
        assertThat(updated).isZero();
        assertThat(unchanged.getStatus()).isEqualTo(FeedGenerationJob.STATUS_PROCESSING);
        assertThat(unchanged.getRetryCount()).isEqualTo(FeedGenerationJobPolicy.MAX_RETRY_COUNT);
        assertThat(unchanged.getProcessingStartedAt()).isEqualTo(staleStartedAt);
    }

    @Test
    void findJobIdsByStatusAndRetryCountLessThan_sortsByCreatedAtThenJobId() {
        FeedGenerationJob third = persistJob(300L, 10L);
        FeedGenerationJob first = persistJob(100L, 10L);
        FeedGenerationJob second = persistJob(200L, 10L);
        LocalDateTime earliest = LocalDateTime.of(2026, 5, 11, 15, 0);
        LocalDateTime latest = LocalDateTime.of(2026, 5, 11, 16, 0);
        updateCreatedAt(third, latest);
        updateCreatedAt(first, earliest);
        updateCreatedAt(second, earliest);
        entityManager.flush();
        entityManager.clear();

        List<FeedGenerationJobRepository.JobIdProjection> jobs =
                feedGenerationJobRepository.findJobIdsByStatusAndRetryCountLessThan(
                FeedGenerationJob.STATUS_PENDING,
                FeedGenerationJobPolicy.MAX_RETRY_COUNT,
                LocalDateTime.of(2026, 5, 11, 17, 0),
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("jobId"))));

        assertThat(jobs).extracting(FeedGenerationJobRepository.JobIdProjection::getJobId)
                .containsExactly(first.getJobId(), second.getJobId(), third.getJobId());
    }

    @Test
    void findPendingJobsReturnsOnlyDueAttempts() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 11, 17, 0);
        FeedGenerationJob due = persistJob(100L, 10L);
        FeedGenerationJob future = persistJob(200L, 10L);
        ReflectionTestUtils.setField(due, "nextAttemptAt", now);
        ReflectionTestUtils.setField(future, "nextAttemptAt", now.plusMinutes(1));
        entityManager.flush();
        entityManager.clear();

        List<FeedGenerationJobRepository.JobIdProjection> jobs =
                feedGenerationJobRepository.findJobIdsByStatusAndRetryCountLessThan(
                        FeedGenerationJob.STATUS_PENDING,
                        FeedGenerationJobPolicy.MAX_RETRY_COUNT,
                        now,
                        PageRequest.of(0, 10));

        assertThat(jobs).extracting(FeedGenerationJobRepository.JobIdProjection::getJobId)
                .containsExactly(due.getJobId());
    }

    @Test
    void redriveFailedResetsRetryStateAndMakesJobDue() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 11, 17, 0);
        FeedGenerationJob job = persistJob(100L, 10L);
        ReflectionTestUtils.setField(job, "status", FeedGenerationJob.STATUS_FAILED);
        ReflectionTestUtils.setField(job, "retryCount", FeedGenerationJobPolicy.MAX_RETRY_COUNT);
        ReflectionTestUtils.setField(job, "lastErrorMessage", "failure");
        entityManager.flush();

        int updated = feedGenerationJobRepository.redriveFailed(job.getJobId(), now);
        entityManager.flush();
        entityManager.clear();

        FeedGenerationJob redriven = entityManager.find(FeedGenerationJob.class, job.getJobId());
        assertThat(updated).isEqualTo(1);
        assertThat(redriven.getStatus()).isEqualTo(FeedGenerationJob.STATUS_PENDING);
        assertThat(redriven.getRetryCount()).isZero();
        assertThat(redriven.getNextAttemptAt()).isEqualTo(now);
        assertThat(redriven.getLastErrorMessage()).isNull();
    }

    private FeedGenerationJob persistJob(Long postId, Long boardId) {
        FeedGenerationJob job = FeedGenerationJob.builder()
                .postId(postId)
                .boardId(boardId)
                .build();
        entityManager.persistAndFlush(job);
        return job;
    }

    private FeedGenerationJob persistProcessingJob(
            Long postId,
            Long boardId,
            LocalDateTime processingStartedAt,
            int retryCount) {
        FeedGenerationJob job = FeedGenerationJob.builder()
                .postId(postId)
                .boardId(boardId)
                .build();
        ReflectionTestUtils.setField(job, "status", FeedGenerationJob.STATUS_PROCESSING);
        ReflectionTestUtils.setField(job, "processingStartedAt", processingStartedAt);
        ReflectionTestUtils.setField(job, "retryCount", retryCount);
        entityManager.persistAndFlush(job);
        return job;
    }

    private void updateCreatedAt(FeedGenerationJob job, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE feed_generation_jobs SET created_at = :createdAt WHERE job_id = :jobId")
                .setParameter("createdAt", createdAt)
                .setParameter("jobId", job.getJobId())
                .executeUpdate();
    }
}
