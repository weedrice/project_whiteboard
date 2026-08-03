-- noviis:migration-phase expand

SET lock_timeout = '5s';

-- noviis:online-index uk_draft_posts_user_client_key
CREATE UNIQUE INDEX CONCURRENTLY uk_draft_posts_user_client_key
    ON draft_posts (user_id, client_draft_key)
    WHERE client_draft_key IS NOT NULL;

RESET lock_timeout;
