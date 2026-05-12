-- Imported from docs/sql/shop-item-entitlement-target-postgres.sql.
-- Flyway manages the transaction boundary for this migration.

-- Manual PostgreSQL schema update for shop entitlement target binding.
-- Run before deploying the shop entitlement handler changes.


ALTER TABLE shop_items
    ADD COLUMN IF NOT EXISTS target_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_shop_items_active_type_target
    ON shop_items (item_type, target_id)
    WHERE is_active = 'Y';
