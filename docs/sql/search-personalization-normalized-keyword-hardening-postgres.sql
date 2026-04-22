-- Manual PostgreSQL update for normalized recent-search deduplication.
-- Run this before deploying the stage-2 recent-search code in environments
-- that still use hibernate ddl-auto=update.

BEGIN;

LOCK TABLE search_personalization IN SHARE ROW EXCLUSIVE MODE;

ALTER TABLE search_personalization
    ADD COLUMN IF NOT EXISTS normalized_keyword VARCHAR(255);

ALTER TABLE search_personalization
    ADD COLUMN IF NOT EXISTS searched_at TIMESTAMP;

DELETE FROM search_personalization
WHERE REGEXP_REPLACE(keyword, '^[[:space:]]+|[[:space:]]+$', '', 'g') = '';

UPDATE search_personalization
SET normalized_keyword = LOWER(REGEXP_REPLACE(keyword, '^[[:space:]]+|[[:space:]]+$', '', 'g')),
    searched_at = COALESCE(searched_at, modified_at, created_at)
WHERE normalized_keyword IS NULL
   OR searched_at IS NULL;

WITH ranked AS (
    SELECT
        log_id,
        ROW_NUMBER() OVER (
            PARTITION BY user_id, normalized_keyword
            ORDER BY searched_at DESC, log_id DESC
        ) AS row_num
    FROM search_personalization
),
deleted AS (
    DELETE FROM search_personalization sp
    USING ranked
    WHERE sp.log_id = ranked.log_id
      AND ranked.row_num > 1
    RETURNING 1
)
SELECT COUNT(*) AS rows_deleted
FROM deleted;

ALTER TABLE search_personalization
    ALTER COLUMN normalized_keyword SET NOT NULL;

ALTER TABLE search_personalization
    ALTER COLUMN searched_at SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_search_personalization_user_normalized_keyword'
    ) THEN
        ALTER TABLE search_personalization
            ADD CONSTRAINT uk_search_personalization_user_normalized_keyword
            UNIQUE (user_id, normalized_keyword);
    END IF;
END
$$;

DROP INDEX IF EXISTS idx_search_personalization_user;

CREATE INDEX idx_search_personalization_user
    ON search_personalization (user_id, searched_at DESC);

COMMIT;
