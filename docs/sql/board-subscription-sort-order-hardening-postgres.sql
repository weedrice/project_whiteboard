-- Backfill board_subscriptions.sort_order deterministically per user before
-- enabling the (user_id, sort_order) uniqueness guarantee in PostgreSQL.

BEGIN;

LOCK TABLE board_subscriptions IN SHARE ROW EXCLUSIVE MODE;

WITH normalized AS (
    SELECT
        user_id,
        board_id,
        ROW_NUMBER() OVER (
            PARTITION BY user_id
            ORDER BY sort_order ASC NULLS LAST, created_at ASC, board_id ASC
        ) AS normalized_sort_order
    FROM board_subscriptions
),
updated AS (
    UPDATE board_subscriptions bs
    SET sort_order = normalized.normalized_sort_order
    FROM normalized
    WHERE bs.user_id = normalized.user_id
      AND bs.board_id = normalized.board_id
      AND bs.sort_order IS DISTINCT FROM normalized.normalized_sort_order
    RETURNING 1
)
SELECT COUNT(*) AS rows_updated
FROM updated;

ALTER TABLE board_subscriptions
    ALTER COLUMN sort_order SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_board_subscriptions_user_sort_order'
    ) THEN
        ALTER TABLE board_subscriptions
            ADD CONSTRAINT uk_board_subscriptions_user_sort_order
            UNIQUE (user_id, sort_order);
    END IF;
END
$$;

COMMIT;
