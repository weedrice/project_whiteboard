-- noviis:migration-phase expand

SET lock_timeout = '5s';

ALTER TABLE push_delivery_jobs
    ADD CONSTRAINT ck_push_delivery_jobs_active_snapshot
        CHECK (
            status IN ('COMPLETED', 'EXPIRED', 'FAILED')
            OR (
                endpoint IS NOT NULL
                AND p256dh IS NOT NULL
                AND auth IS NOT NULL
                AND payload IS NOT NULL
            )
        ) NOT VALID;

RESET lock_timeout;
