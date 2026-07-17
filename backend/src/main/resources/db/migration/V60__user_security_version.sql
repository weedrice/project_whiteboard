-- migration-phase: expand
-- noviis:migration-phase expand
ALTER TABLE users
    ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0;
