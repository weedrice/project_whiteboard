-- noviis:migration-phase expand

ALTER TABLE files
    ADD COLUMN image_width INTEGER,
    ADD COLUMN image_height INTEGER,
    ADD COLUMN expected_variant_count INTEGER,
    ADD COLUMN variant_reconciliation_version INTEGER;

ALTER TABLE files
    ADD CONSTRAINT ck_files_image_width_positive CHECK (image_width IS NULL OR image_width > 0),
    ADD CONSTRAINT ck_files_image_height_positive CHECK (image_height IS NULL OR image_height > 0),
    ADD CONSTRAINT ck_files_expected_variant_count CHECK (
        expected_variant_count IS NULL OR expected_variant_count BETWEEN 0 AND 2
    ),
    ADD CONSTRAINT ck_files_variant_reconciliation_version CHECK (
        variant_reconciliation_version IS NULL OR variant_reconciliation_version > 0
    );

CREATE INDEX idx_files_variant_reconciliation
    ON files (storage_status, variant_reconciliation_version, file_id)
    WHERE mime_type IN ('image/jpeg', 'image/png', 'image/webp');
