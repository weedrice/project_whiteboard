-- noviis:migration-phase expand

SET lock_timeout = '5s';

ALTER TABLE push_delivery_jobs
    ALTER COLUMN endpoint DROP NOT NULL,
    ALTER COLUMN p256dh DROP NOT NULL,
    ALTER COLUMN auth DROP NOT NULL,
    ALTER COLUMN payload DROP NOT NULL;

UPDATE push_delivery_jobs
SET endpoint = NULL,
    p256dh = NULL,
    auth = NULL,
    payload = NULL
WHERE status IN ('COMPLETED', 'EXPIRED', 'FAILED');

-- noviis:online-index idx_push_delivery_jobs_receiver_status
CREATE INDEX CONCURRENTLY idx_push_delivery_jobs_receiver_status
    ON push_delivery_jobs (receiver_user_id, status);
