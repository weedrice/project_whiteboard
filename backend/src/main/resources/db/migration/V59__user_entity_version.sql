-- migration-phase: expand
-- noviis:migration-phase expand
ALTER TABLE users
    ADD COLUMN entity_version BIGINT NOT NULL DEFAULT 0;
