-- Manual PostgreSQL update for message_queue processing lease recovery.
-- Run this before deploying the processing recovery code in environments
-- that still use hibernate ddl-auto=update.

BEGIN;

LOCK TABLE message_queue IN SHARE ROW EXCLUSIVE MODE;

ALTER TABLE message_queue
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS send_attempt_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS send_attempt_started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS send_attempt_confirmed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS delivery_uncertain_at TIMESTAMP;

-- Existing PROCESSING rows receive a fresh lease at rollout time to avoid
-- reclaiming in-flight sends immediately after deployment. Truly stale rows
-- are recovered by the scheduler after the lease window elapses.
UPDATE message_queue
SET processing_started_at = CURRENT_TIMESTAMP
WHERE status = 'PROCESSING'
  AND processing_started_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_message_queue_processing_started
    ON message_queue (status, processing_started_at);

CREATE INDEX IF NOT EXISTS idx_message_queue_send_attempt
    ON message_queue (send_attempt_id)
    WHERE send_attempt_id IS NOT NULL;

-- If the known status check constraint exists in the target environment,
-- extend it before deploying code that writes DELIVERED_UNCONFIRMED.
-- Audit environments with a differently named status constraint manually.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_message_queue_status'
          AND conrelid = 'message_queue'::regclass
    ) THEN
        ALTER TABLE message_queue DROP CONSTRAINT ck_message_queue_status;

        ALTER TABLE message_queue
            ADD CONSTRAINT ck_message_queue_status
            CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'DELIVERED_UNCONFIRMED'));
    END IF;
END $$;

COMMIT;
