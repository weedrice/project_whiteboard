-- noviis:migration-phase expand

ALTER TABLE shop_items
    ADD COLUMN is_sale_enabled VARCHAR(1) NOT NULL DEFAULT 'Y',
    ADD CONSTRAINT ck_shop_items_is_sale_enabled CHECK (is_sale_enabled IN ('Y', 'N'));

UPDATE shop_items
SET is_sale_enabled = 'N'
WHERE target_id IS NULL;
