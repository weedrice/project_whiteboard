-- noviis:migration-phase expand

SET lock_timeout = '5s';

-- noviis:online-index idx_tags_orphan_cleanup
CREATE INDEX CONCURRENTLY idx_tags_orphan_cleanup
    ON tags (created_at, tag_id)
    WHERE post_count = 0;

RESET lock_timeout;
