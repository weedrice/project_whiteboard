-- noviis:migration-phase expand

SET lock_timeout = '5s';

ALTER TABLE feed_generation_jobs
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP(6);

UPDATE feed_generation_jobs
SET next_attempt_at = COALESCE(modified_at, created_at, CURRENT_TIMESTAMP)
WHERE next_attempt_at IS NULL;

-- noviis:online-index idx_feed_generation_jobs_due
CREATE INDEX CONCURRENTLY idx_feed_generation_jobs_due
    ON feed_generation_jobs (status, next_attempt_at, created_at, job_id);
