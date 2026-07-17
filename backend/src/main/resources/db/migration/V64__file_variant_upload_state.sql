-- migration-phase: expand
-- noviis:migration-phase expand

ALTER TABLE file_variants
    ADD COLUMN storage_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE file_variants
    ADD CONSTRAINT ck_file_variants_storage_status
        CHECK (storage_status IN ('PENDING_UPLOAD', 'ACTIVE', 'PENDING_DELETE'));

CREATE INDEX idx_file_variants_storage_status_created
    ON file_variants (storage_status, created_at, file_variant_id);
