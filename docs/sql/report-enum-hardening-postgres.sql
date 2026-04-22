-- Manual PostgreSQL update for report enum normalization and constraints.
-- Run this before deploying the report enum hardening code in environments
-- that still use hibernate ddl-auto=update.

BEGIN;

LOCK TABLE reports IN SHARE ROW EXCLUSIVE MODE;

WITH ranked AS (
    SELECT
        report_id,
        ROW_NUMBER() OVER (
            PARTITION BY reporter_id, UPPER(BTRIM(target_type)), target_id
            ORDER BY created_at DESC, report_id DESC
        ) AS row_num
    FROM reports
),
deleted AS (
    DELETE FROM reports r
    USING ranked
    WHERE r.report_id = ranked.report_id
      AND ranked.row_num > 1
    RETURNING 1
)
SELECT COUNT(*) AS rows_deleted
FROM deleted;

UPDATE reports
SET target_type = UPPER(BTRIM(target_type))
WHERE target_type IS NOT NULL
  AND target_type <> UPPER(BTRIM(target_type));

UPDATE reports
SET status = UPPER(BTRIM(status))
WHERE status IS NOT NULL
  AND status <> UPPER(BTRIM(status));

UPDATE reports
SET reason_type = CASE
    WHEN reason_type IS NULL OR BTRIM(reason_type) = '' THEN 'ETC'
    WHEN UPPER(BTRIM(reason_type)) IN ('SPAM', 'ABUSE', 'ADULT', 'ETC') THEN UPPER(BTRIM(reason_type))
    ELSE 'ETC'
END
WHERE reason_type IS NULL
   OR BTRIM(reason_type) = ''
   OR reason_type <> CASE
       WHEN UPPER(BTRIM(reason_type)) IN ('SPAM', 'ABUSE', 'ADULT', 'ETC') THEN UPPER(BTRIM(reason_type))
       ELSE 'ETC'
   END;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_reports_user_target'
    ) THEN
        ALTER TABLE reports
            ADD CONSTRAINT uk_reports_user_target
            UNIQUE (reporter_id, target_type, target_id);
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_reports_target_type'
    ) THEN
        ALTER TABLE reports
            ADD CONSTRAINT ck_reports_target_type
            CHECK (target_type IN ('POST', 'COMMENT', 'USER'));
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_reports_status'
    ) THEN
        ALTER TABLE reports
            ADD CONSTRAINT ck_reports_status
            CHECK (status IN ('PENDING', 'RESOLVED', 'REJECTED'));
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_reports_reason_type'
    ) THEN
        ALTER TABLE reports
            ADD CONSTRAINT ck_reports_reason_type
            CHECK (reason_type IN ('SPAM', 'ABUSE', 'ADULT', 'ETC'));
    END IF;
END
$$;

COMMIT;
