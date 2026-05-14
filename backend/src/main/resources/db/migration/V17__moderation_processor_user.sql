-- Flyway manages the transaction boundary for this migration.

ALTER TABLE sanctions
    ADD COLUMN IF NOT EXISTS processor_user_id BIGINT;

UPDATE sanctions
SET processor_user_id = admins.user_id
FROM admins
WHERE sanctions.processor_user_id IS NULL
  AND sanctions.admin_id = admins.admin_id;

ALTER TABLE sanctions
    ALTER COLUMN admin_id DROP NOT NULL;

ALTER TABLE sanctions
    ALTER COLUMN processor_user_id SET NOT NULL;

ALTER TABLE sanctions
    ADD CONSTRAINT fk_sanctions_processor_user
        FOREIGN KEY (processor_user_id) REFERENCES users (user_id);

CREATE INDEX IF NOT EXISTS idx_sanctions_processor_user
    ON sanctions (processor_user_id);

ALTER TABLE ip_blocks
    ADD COLUMN IF NOT EXISTS processor_user_id BIGINT;

UPDATE ip_blocks
SET processor_user_id = admins.user_id
FROM admins
WHERE ip_blocks.processor_user_id IS NULL
  AND ip_blocks.admin_id = admins.admin_id;

ALTER TABLE ip_blocks
    ALTER COLUMN admin_id DROP NOT NULL;

ALTER TABLE ip_blocks
    ALTER COLUMN processor_user_id SET NOT NULL;

ALTER TABLE ip_blocks
    ADD CONSTRAINT fk_ip_blocks_processor_user
        FOREIGN KEY (processor_user_id) REFERENCES users (user_id);

CREATE INDEX IF NOT EXISTS idx_ip_blocks_processor_user
    ON ip_blocks (processor_user_id);
