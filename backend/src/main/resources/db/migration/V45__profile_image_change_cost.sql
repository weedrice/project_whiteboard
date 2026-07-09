ALTER TABLE users
    ADD COLUMN IF NOT EXISTS profile_image_change_free_used VARCHAR(1) NOT NULL DEFAULT 'N';

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS ck_users_profile_image_change_free_used;

ALTER TABLE users
    ADD CONSTRAINT ck_users_profile_image_change_free_used
        CHECK (profile_image_change_free_used IN ('Y', 'N'));

INSERT INTO global_configs (config_key, config_value, description, created_at, modified_at)
VALUES ('POINT_PROFILE_IMAGE_CHANGE_COST', '1000', 'Profile image change point cost', NOW(), NOW())
ON CONFLICT (config_key) DO NOTHING;
