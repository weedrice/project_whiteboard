-- Manual PostgreSQL schema update for purpose-scoped verification tickets.
-- Run after taking a backup and before deploying the stage-1 auth changes.

BEGIN;

ALTER TABLE verification_codes
    ADD COLUMN IF NOT EXISTS purpose VARCHAR(30);

UPDATE verification_codes
SET purpose = 'SIGNUP'
WHERE purpose IS NULL;

ALTER TABLE verification_codes
    ALTER COLUMN purpose SET NOT NULL;

ALTER TABLE verification_codes
    ADD COLUMN IF NOT EXISTS verification_ticket VARCHAR(64);

ALTER TABLE verification_codes
    ADD COLUMN IF NOT EXISTS ticket_expiry_date TIMESTAMP;

ALTER TABLE verification_codes
    ADD COLUMN IF NOT EXISTS is_ticket_consumed CHAR(1);

UPDATE verification_codes
SET is_ticket_consumed = 'N'
WHERE is_ticket_consumed IS NULL;

ALTER TABLE verification_codes
    ALTER COLUMN is_ticket_consumed SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_verification_codes_email_purpose_created_at
    ON verification_codes (email, purpose, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_verification_codes_ticket_lookup
    ON verification_codes (email, purpose, verification_ticket);

COMMIT;

-- Optional audit before rollout.
SELECT verification_id, email, purpose, delivery_status, verification_ticket, ticket_expiry_date, is_ticket_consumed
FROM verification_codes
WHERE purpose IS NULL
   OR is_ticket_consumed IS NULL;
