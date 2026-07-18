-- noviis:migration-phase expand

SET lock_timeout = '5s';

-- noviis:online-index idx_push_delivery_jobs_failed_cleanup
CREATE INDEX CONCURRENTLY idx_push_delivery_jobs_failed_cleanup
    ON push_delivery_jobs (last_failed_at, job_id)
    WHERE status = 'FAILED';

RESET lock_timeout;
