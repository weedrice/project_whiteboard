-- noviis:migration-phase expand
-- Preserve existing public profiles and inserts from the previous application.
ALTER TABLE user_settings
    ADD COLUMN profile_overview_public VARCHAR(1) NOT NULL DEFAULT 'Y',
    ADD COLUMN profile_posts_public VARCHAR(1) NOT NULL DEFAULT 'Y',
    ADD COLUMN profile_comments_public VARCHAR(1) NOT NULL DEFAULT 'Y';

-- Activate only after rollback targets all enforce saved profile privacy settings.
INSERT INTO global_configs (config_key, config_value, description, created_at, modified_at)
VALUES ('PROFILE_PRIVACY_ENABLED', 'N',
        'Enables profile privacy changes after the rollback window; activation is one-way',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
