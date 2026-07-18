-- Restartable PostgreSQL backfill for purchase-history presentation snapshots.
-- V82 only adds nullable columns so old and new application versions can run together.
-- Run this file repeatedly. Each execution updates at most 1,000 rows.
-- Backfill is complete when the UPDATE reports 0 rows.

BEGIN;

WITH batch AS (
    SELECT ph.purchase_id,
           si.item_name,
           si.item_type,
           si.image_url
    FROM purchase_history ph
    JOIN shop_items si ON si.item_id = ph.item_id
    WHERE ph.item_name_snapshot IS NULL
    ORDER BY ph.purchase_id
    LIMIT 1000
    FOR UPDATE OF ph SKIP LOCKED
)
UPDATE purchase_history ph
SET item_name_snapshot = batch.item_name,
    item_type_snapshot = batch.item_type,
    image_url_snapshot = batch.image_url
FROM batch
WHERE ph.purchase_id = batch.purchase_id;

COMMIT;

-- Progress check. Remaining reaches 0 when the backfill is complete.
SELECT COUNT(*) AS remaining
FROM purchase_history
WHERE item_name_snapshot IS NULL;
