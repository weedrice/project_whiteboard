-- noviis:migration-phase expand

SET lock_timeout = '5s';

-- noviis:online-index idx_push_delivery_jobs_terminal_cleanup
CREATE INDEX CONCURRENTLY idx_push_delivery_jobs_terminal_cleanup
    ON push_delivery_jobs (modified_at, job_id)
    WHERE status IN ('COMPLETED', 'EXPIRED');

RESET lock_timeout;
