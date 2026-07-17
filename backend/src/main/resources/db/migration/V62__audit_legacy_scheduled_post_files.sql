-- migration-phase: backfill
-- noviis:migration-phase backfill

CREATE TEMP TABLE legacy_scheduled_post_file_refs_raw ON COMMIT DROP AS
SELECT sp.scheduled_post_id,
       sp.draft_id,
       legacy_file.file_id_text::BIGINT AS file_id,
       legacy_file.ordinal::INTEGER AS file_order
FROM scheduled_posts sp
    CROSS JOIN LATERAL jsonb_array_elements_text(
        COALESCE(NULLIF(sp.file_ids_json, ''), '[]')::jsonb
    ) WITH ORDINALITY AS legacy_file(file_id_text, ordinal)
WHERE sp.file_ids_json IS NOT NULL
  AND sp.status IN ('SCHEDULED', 'PUBLISHING', 'FAILED')
  AND jsonb_typeof(COALESCE(NULLIF(sp.file_ids_json, ''), '[]')::jsonb) = 'array';

DO $$
DECLARE
    invalid_reference_count BIGINT;
    duplicate_reference_count BIGINT;
BEGIN
    SELECT COUNT(*)
    INTO duplicate_reference_count
    FROM (
        SELECT file_id
        FROM legacy_scheduled_post_file_refs_raw
        GROUP BY file_id
        HAVING COUNT(*) > 1
    ) duplicates;

    IF duplicate_reference_count > 0 THEN
        RAISE EXCEPTION
            'Legacy scheduled post file audit failed: % duplicate file reference(s)',
            duplicate_reference_count;
    END IF;

    SELECT COUNT(*)
    INTO invalid_reference_count
    FROM legacy_scheduled_post_file_refs_raw raw
    LEFT JOIN files f ON f.file_id = raw.file_id
    WHERE f.file_id IS NULL
       OR (f.storage_status IS NOT NULL AND f.storage_status <> 'ACTIVE')
       OR NOT (
           (f.related_id IS NULL AND f.related_type IS NULL)
           OR (
               raw.draft_id IS NOT NULL
               AND f.related_type = 'DRAFT_POST'
               AND f.related_id = raw.draft_id
           )
       );

    IF invalid_reference_count > 0 THEN
        RAISE EXCEPTION
            'Legacy scheduled post file audit failed: % invalid file reference(s)',
            invalid_reference_count;
    END IF;
END
$$;

INSERT INTO scheduled_post_files (scheduled_post_id, file_id, file_order)
SELECT raw.scheduled_post_id, raw.file_id, raw.file_order
FROM legacy_scheduled_post_file_refs_raw raw
ON CONFLICT (scheduled_post_id, file_id) DO NOTHING;

DO $$
DECLARE
    missing_reference_count BIGINT;
    mismatched_order_count BIGINT;
BEGIN
    SELECT COUNT(*)
    INTO missing_reference_count
    FROM legacy_scheduled_post_file_refs_raw raw
    LEFT JOIN scheduled_post_files protected
      ON protected.scheduled_post_id = raw.scheduled_post_id
     AND protected.file_id = raw.file_id
    WHERE protected.file_id IS NULL;

    SELECT COUNT(*)
    INTO mismatched_order_count
    FROM legacy_scheduled_post_file_refs_raw raw
    JOIN scheduled_post_files protected
      ON protected.scheduled_post_id = raw.scheduled_post_id
     AND protected.file_id = raw.file_id
    WHERE protected.file_order <> raw.file_order;

    IF missing_reference_count > 0 OR mismatched_order_count > 0 THEN
        RAISE EXCEPTION
            'Legacy scheduled post file audit failed after backfill: missing=%, order_mismatch=%',
            missing_reference_count,
            mismatched_order_count;
    END IF;
END
$$;
