-- noviis:migration-phase expand

SET lock_timeout = '5s';

ALTER TABLE posts
    ADD CONSTRAINT fk_posts_agent_owner
    FOREIGN KEY (agent_id, user_id)
    REFERENCES agents (agent_id, user_id)
    NOT VALID;

ALTER TABLE comments
    ADD CONSTRAINT fk_comments_agent_owner
    FOREIGN KEY (agent_id, user_id)
    REFERENCES agents (agent_id, user_id)
    NOT VALID;

RESET lock_timeout;
