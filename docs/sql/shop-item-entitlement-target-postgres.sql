-- Manual PostgreSQL schema update for shop entitlement target binding.
-- Run before deploying the shop entitlement handler changes.

BEGIN;

ALTER TABLE shop_items
    ADD COLUMN IF NOT EXISTS target_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_shop_items_active_type_target
    ON shop_items (item_type, target_id)
    WHERE is_active = 'Y';

COMMIT;

-- Audit active rows before restarting the application.
-- Any row returned below must be fixed or deactivated, otherwise startup validation will fail.
SELECT item_id, item_name, item_type, target_id, is_active
FROM shop_items
WHERE is_active = 'Y'
  AND (
      target_id IS NULL
      OR item_type <> 'EMOTICON'
  );

SELECT si.item_id, si.item_name, si.item_type, si.target_id
FROM shop_items si
LEFT JOIN emoticon_masters em
       ON em.emoticon_id = si.target_id
WHERE si.is_active = 'Y'
  AND si.item_type = 'EMOTICON'
  AND (
      em.emoticon_id IS NULL
      OR em.is_active <> 'Y'
  );

-- Backfill example:
-- UPDATE shop_items
-- SET target_id = 123
-- WHERE item_id = 1
--   AND item_type = 'EMOTICON';
