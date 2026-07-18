-- noviis:migration-phase expand

ALTER TABLE purchase_history
    ADD COLUMN IF NOT EXISTS item_name_snapshot VARCHAR(100),
    ADD COLUMN IF NOT EXISTS item_type_snapshot VARCHAR(50),
    ADD COLUMN IF NOT EXISTS image_url_snapshot VARCHAR(500);
