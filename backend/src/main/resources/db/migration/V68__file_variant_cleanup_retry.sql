-- migration-phase: expand
-- noviis:migration-phase expand

ALTER TABLE file_variants
    ADD COLUMN cleanup_retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN cleanup_next_attempt_at TIMESTAMP(6),
    ADD COLUMN cleanup_last_error VARCHAR(100);

ALTER TABLE file_variants
    ADD CONSTRAINT ck_file_variants_cleanup_retry_count CHECK (cleanup_retry_count >= 0);

CREATE INDEX idx_file_variants_cleanup_due
    ON file_variants (storage_status, cleanup_next_attempt_at, created_at, file_variant_id);
