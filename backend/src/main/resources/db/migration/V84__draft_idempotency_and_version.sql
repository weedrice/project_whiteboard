-- noviis:migration-phase expand

ALTER TABLE draft_posts
    ADD COLUMN client_draft_key VARCHAR(64),
    ADD COLUMN entity_version BIGINT NOT NULL DEFAULT 0;
