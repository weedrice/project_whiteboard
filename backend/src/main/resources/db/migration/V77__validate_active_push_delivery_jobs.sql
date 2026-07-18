-- noviis:migration-phase backfill

SET lock_timeout = '5s';

ALTER TABLE push_delivery_jobs
    VALIDATE CONSTRAINT ck_push_delivery_jobs_active_snapshot;

RESET lock_timeout;
