ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS is_blinded VARCHAR(1) NOT NULL DEFAULT 'N',
    ADD COLUMN IF NOT EXISTS blind_reason VARCHAR(50),
    ADD COLUMN IF NOT EXISTS blinded_at TIMESTAMP(6);

ALTER TABLE comments
    ADD COLUMN IF NOT EXISTS is_blinded VARCHAR(1) NOT NULL DEFAULT 'N',
    ADD COLUMN IF NOT EXISTS blind_reason VARCHAR(50),
    ADD COLUMN IF NOT EXISTS blinded_at TIMESTAMP(6);

ALTER TABLE posts
    DROP CONSTRAINT IF EXISTS ck_posts_is_blinded;

ALTER TABLE posts
    ADD CONSTRAINT ck_posts_is_blinded
        CHECK (is_blinded IN ('Y', 'N'));

ALTER TABLE comments
    DROP CONSTRAINT IF EXISTS ck_comments_is_blinded;

ALTER TABLE comments
    ADD CONSTRAINT ck_comments_is_blinded
        CHECK (is_blinded IN ('Y', 'N'));

INSERT INTO global_configs (config_key, config_value, description, created_at, modified_at)
VALUES ('REPORT_AUTO_BLIND_THRESHOLD', '5', '자동 블라인드 처리에 필요한 대기 신고 수', NOW(), NOW())
ON CONFLICT (config_key) DO NOTHING;
