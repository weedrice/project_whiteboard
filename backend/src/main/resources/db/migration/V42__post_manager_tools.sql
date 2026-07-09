ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS pinned_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_posts_board_pinned
    ON posts (board_id, is_deleted, is_blinded, is_secret, pinned_at DESC, created_at DESC, post_id DESC);
