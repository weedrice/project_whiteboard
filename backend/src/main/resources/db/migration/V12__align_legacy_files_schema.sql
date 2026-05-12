-- Replaces the legacy FileSchemaInitializer runtime schema alignment.

ALTER TABLE files
    ADD COLUMN IF NOT EXISTS storage_status VARCHAR(30);

UPDATE files
SET storage_status = 'ACTIVE'
WHERE storage_status IS NULL;

ALTER TABLE files
    ALTER COLUMN storage_status SET DEFAULT 'ACTIVE';

ALTER TABLE files
    ALTER COLUMN storage_status SET NOT NULL;

ALTER TABLE files
    ADD COLUMN IF NOT EXISTS delete_requested_at TIMESTAMP;

ALTER TABLE files
    ADD COLUMN IF NOT EXISTS delete_retry_count INTEGER;

UPDATE files
SET delete_retry_count = 0
WHERE delete_retry_count IS NULL;

ALTER TABLE files
    ALTER COLUMN delete_retry_count SET DEFAULT 0;

ALTER TABLE files
    ALTER COLUMN delete_retry_count SET NOT NULL;

ALTER TABLE files
    ADD COLUMN IF NOT EXISTS delete_last_error VARCHAR(1000);