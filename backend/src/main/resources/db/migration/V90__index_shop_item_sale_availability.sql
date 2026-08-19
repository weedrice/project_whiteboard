-- noviis:migration-phase expand

SET lock_timeout = '5s';

-- noviis:online-index idx_shop_items_sale_availability
CREATE INDEX CONCURRENTLY idx_shop_items_sale_availability
    ON shop_items (is_active, is_sale_enabled, item_type, item_id);

RESET lock_timeout;
