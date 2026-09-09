-- noviis:migration-phase expand

SET lock_timeout = '5s';

ALTER TABLE posts
    VALIDATE CONSTRAINT fk_posts_agent_owner;

ALTER TABLE comments
    VALIDATE CONSTRAINT fk_comments_agent_owner;

RESET lock_timeout;
