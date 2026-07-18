-- noviis:migration-phase expand

SET lock_timeout = '5s';

-- noviis:online-index idx_notification_delivery_jobs_completed_cleanup
CREATE INDEX CONCURRENTLY idx_notification_delivery_jobs_completed_cleanup
    ON notification_delivery_jobs (modified_at, job_id)
    WHERE status = 'COMPLETED';

-- noviis:online-index idx_notification_delivery_jobs_failed_cleanup
CREATE INDEX CONCURRENTLY idx_notification_delivery_jobs_failed_cleanup
    ON notification_delivery_jobs (last_failed_at, job_id)
    WHERE status = 'FAILED';

RESET lock_timeout;
