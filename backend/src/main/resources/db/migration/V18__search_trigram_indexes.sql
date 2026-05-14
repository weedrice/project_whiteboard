-- Improve substring search performance without changing current LIKE semantics.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_posts_title_trgm
    ON posts USING gin (lower(title) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_posts_contents_trgm
    ON posts USING gin (lower(contents) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_comments_content_trgm
    ON comments USING gin (lower(content) gin_trgm_ops);
