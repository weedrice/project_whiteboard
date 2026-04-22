-- Manual PostgreSQL update for message_queue processing lease recovery.
-- Run this before deploying the processing recovery code in environments
-- that still use hibernate ddl-auto=update.

BEGIN;

LOCK TABLE message_queue IN SHARE ROW EXCLUSIVE MODE;

ALTER TABLE message_queue
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMP;

-- Existing PROCESSING rows receive a fresh lease at rollout time to avoid
-- reclaiming in-flight sends immediately after deployment. Truly stale rows
-- are recovered by the scheduler after the lease window elapses.
UPDATE message_queue
SET processing_started_at = CURRENT_TIMESTAMP
WHERE status = 'PROCESSING'
  AND processing_started_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_message_queue_processing_started
    ON message_queue (status, processing_started_at);

COMMIT;
