package com.weedrice.whiteboard.domain.feed.repository;

import com.weedrice.whiteboard.domain.feed.FeedGenerationJobPolicy;
import com.weedrice.whiteboard.domain.feed.entity.FeedGenerationJob;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
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
                "failure");

        entityManager.flush();
        entityManager.clear();

        FeedGenerationJob failed = entityManager.find(FeedGenerationJob.class, job.getJobId());
        assertThat(updated).isEqualTo(1);
        assertThat(failed.getStatus()).isEqualTo(FeedGenerationJob.STATUS_PENDING);
        assertThat(failed.getRetryCount()).isEqualTo(4);
        assertThat(failed.getProcessingStartedAt()).isNull();
        assertThat(failed.getLastErrorMessage()).isEqualTo("failure");
    }

    @Test
    void markFailedIfCurrent_marksFailedAtRetryLimit() {
        LocalDateTime claimedAt = LocalDateTime.of(2026, 5, 11, 16, 30);
        FeedGenerationJob job = persistProcessingJob(100L, 10L, claimedAt, 4);

        int updated = feedGenerationJobRepository.markFailedIfCurrent(
                job.getJobId(),
                claimedAt,
                FeedGenerationJobPolicy.MAX_RETRY_COUNT,
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
                "Processing lease expired");

        entityManager.flush();
        entityManager.clear();

        FeedGenerationJob recovered = entityManager.find(FeedGenerationJob.class, job.getJobId());
        assertThat(updated).isEqualTo(1);
        assertThat(recovered.getStatus()).isEqualTo(FeedGenerationJob.STATUS_PENDING);
        assertThat(recovered.getRetryCount()).isEqualTo(1);
        assertThat(recovered.getProcessingStartedAt()).isNull();
        assertThat(recovered.getLastErrorMessage()).isEqualTo("Processing lease expired");
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
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("jobId"))));

        assertThat(jobs).extracting(FeedGenerationJobRepository.JobIdProjection::getJobId)
                .containsExactly(first.getJobId(), second.getJobId(), third.getJobId());
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
