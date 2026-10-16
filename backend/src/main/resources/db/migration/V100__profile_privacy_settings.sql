-- noviis:migration-phase expand
-- Preserve existing public profiles and inserts from the previous application.
ALTER TABLE user_settings
    ADD COLUMN profile_overview_public VARCHAR(1) NOT NULL DEFAULT 'Y',
    ADD COLUMN profile_posts_public VARCHAR(1) NOT NULL DEFAULT 'Y',
    ADD COLUMN profile_comments_public VARCHAR(1) NOT NULL DEFAULT 'Y';
