CREATE TABLE IF NOT EXISTS file_variants (
    file_variant_id BIGSERIAL PRIMARY KEY,
    file_id BIGINT NOT NULL,
    variant_type VARCHAR(30) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    created_at TIMESTAMP,
    modified_at TIMESTAMP,
    CONSTRAINT fk_file_variants_file
        FOREIGN KEY (file_id)
        REFERENCES files(file_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_file_variants_type
        CHECK (variant_type IN ('THUMBNAIL', 'MEDIUM')),
    CONSTRAINT uk_file_variants_file_type
        UNIQUE (file_id, variant_type)
);

CREATE INDEX IF NOT EXISTS idx_file_variants_file
    ON file_variants(file_id);
