-- noviis:migration-phase expand

SET lock_timeout = '5s';

-- noviis:online-index idx_draft_posts_expired_cleanup
CREATE INDEX CONCURRENTLY idx_draft_posts_expired_cleanup
    ON draft_posts (modified_at, draft_id);

RESET lock_timeout;
