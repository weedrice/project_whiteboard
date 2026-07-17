-- migration-phase: expand
-- noviis:migration-phase expand
CREATE TABLE scheduled_post_files (
    scheduled_post_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    file_order INTEGER NOT NULL,
    PRIMARY KEY (scheduled_post_id, file_id),
    CONSTRAINT fk_scheduled_post_files_scheduled_post
        FOREIGN KEY (scheduled_post_id) REFERENCES scheduled_posts (scheduled_post_id) ON DELETE CASCADE,
    CONSTRAINT fk_scheduled_post_files_file
        FOREIGN KEY (file_id) REFERENCES files (file_id),
    CONSTRAINT uk_scheduled_post_files_file UNIQUE (file_id),
    CONSTRAINT uk_scheduled_post_files_order UNIQUE (scheduled_post_id, file_order),
    CONSTRAINT ck_scheduled_post_files_order CHECK (file_order > 0)
);

CREATE TEMP TABLE legacy_scheduled_post_file_refs AS
SELECT sp.scheduled_post_id,
       legacy_file.file_id_text::BIGINT AS file_id,
       legacy_file.ordinal::INTEGER AS file_order
FROM scheduled_posts sp
    CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(NULLIF(file_ids_json, ''), '[]')::jsonb)
        WITH ORDINALITY AS legacy_file(file_id_text, ordinal)
    JOIN files f ON f.file_id = legacy_file.file_id_text::BIGINT
WHERE sp.file_ids_json IS NOT NULL
  AND sp.status IN ('SCHEDULED', 'PUBLISHING', 'FAILED')
  AND jsonb_typeof(COALESCE(NULLIF(sp.file_ids_json, ''), '[]')::jsonb) = 'array'
  AND (f.storage_status IS NULL OR f.storage_status = 'ACTIVE')
  AND (
      (f.related_id IS NULL AND f.related_type IS NULL)
      OR (sp.draft_id IS NOT NULL AND f.related_type = 'DRAFT_POST' AND f.related_id = sp.draft_id)
  );

DO $$
BEGIN
    IF EXISTS (
        SELECT file_id
        FROM legacy_scheduled_post_file_refs
        GROUP BY file_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'A file is referenced by more than one active scheduled post';
    END IF;
END
$$;

INSERT INTO scheduled_post_files (scheduled_post_id, file_id, file_order)
SELECT scheduled_post_id, file_id, file_order
FROM legacy_scheduled_post_file_refs;
