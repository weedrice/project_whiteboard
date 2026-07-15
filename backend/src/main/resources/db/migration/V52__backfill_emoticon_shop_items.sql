-- Keep the legacy Noviicon price key as the source for prices captured when a
-- shop item is first created. Existing target-bound item prices are preserved.
ALTER TABLE shop_items
    ALTER COLUMN image_url TYPE VARCHAR(500);

INSERT INTO global_configs (config_key, config_value, description, created_at, modified_at)
VALUES ('NOBICON_PRICE', '100', 'Default Noviicon purchase price', NOW(), NOW())
ON CONFLICT (config_key) DO NOTHING;

-- Normalize legacy invalid values before using the setting for backfill.
UPDATE global_configs
SET config_value = '100',
    modified_at = NOW()
WHERE config_key = 'NOBICON_PRICE'
  AND NOT CASE
      WHEN BTRIM(config_value) ~ '^[0-9]+$'
          THEN BTRIM(config_value)::NUMERIC BETWEEN 0 AND 2147483647
      ELSE FALSE
  END;

-- Legacy untargeted rows cannot be mapped deterministically. Keep them for
-- purchase-history references, but ensure they are not offered for sale.
UPDATE shop_items
SET is_active = 'N',
    modified_at = NOW()
WHERE item_type = 'EMOTICON'
  AND target_id IS NULL
  AND is_active = 'Y';

-- Detach orphan targets while retaining the shop item for historical reads.
UPDATE shop_items si
SET is_active = 'N',
    target_id = NULL,
    modified_at = NOW()
WHERE si.item_type = 'EMOTICON'
  AND si.target_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM emoticon_masters em
      WHERE em.emoticon_id = si.target_id
  );

-- Select one canonical item per emoticon. Prefer the currently active item,
-- then the oldest item id. Detach duplicates instead of deleting them so any
-- purchase_history foreign-key references remain valid.
WITH ranked_items AS (
    SELECT item_id,
           ROW_NUMBER() OVER (
               PARTITION BY target_id
               ORDER BY CASE WHEN is_active = 'Y' THEN 0 ELSE 1 END, item_id
           ) AS row_number
    FROM shop_items
    WHERE item_type = 'EMOTICON'
      AND target_id IS NOT NULL
)
UPDATE shop_items si
SET is_active = 'N',
    target_id = NULL,
    modified_at = NOW()
FROM ranked_items ranked
WHERE si.item_id = ranked.item_id
  AND ranked.row_number > 1;

-- Backfill every active and inactive emoticon that has no canonical item.
-- The price is captured from NOBICON_PRICE at migration time.
INSERT INTO shop_items (
    item_name,
    description,
    price,
    item_type,
    target_id,
    image_url,
    is_active,
    created_at,
    modified_at
)
SELECT em.name,
       NULL,
       (SELECT BTRIM(config_value)::INTEGER
        FROM global_configs
        WHERE config_key = 'NOBICON_PRICE'),
       'EMOTICON',
       em.emoticon_id,
       em.thumbnail_url,
       em.is_active,
       NOW(),
       NOW()
FROM emoticon_masters em
WHERE NOT EXISTS (
    SELECT 1
    FROM shop_items si
    WHERE si.item_type = 'EMOTICON'
      AND si.target_id = em.emoticon_id
);

-- Emoticon visibility and catalog metadata are authoritative. Do not update
-- price: it is a creation-time snapshot and existing prices must be preserved.
UPDATE shop_items si
SET item_name = em.name,
    image_url = em.thumbnail_url,
    is_active = em.is_active,
    modified_at = NOW()
FROM emoticon_masters em
WHERE si.item_type = 'EMOTICON'
  AND si.target_id = em.emoticon_id
  AND (
      si.item_name IS DISTINCT FROM em.name
      OR si.image_url IS DISTINCT FROM em.thumbnail_url
      OR si.is_active IS DISTINCT FROM em.is_active
  );

-- A Noviicon has exactly one target-bound shop item across all visibility
-- states. Retired and legacy history rows remain allowed with a NULL target.
CREATE UNIQUE INDEX uk_shop_items_emoticon_target
    ON shop_items (target_id)
    WHERE item_type = 'EMOTICON'
      AND target_id IS NOT NULL;
