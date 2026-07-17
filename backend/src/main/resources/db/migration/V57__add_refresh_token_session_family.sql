-- migration-phase: expand
ALTER TABLE refresh_tokens
    ADD COLUMN session_family_id UUID;

UPDATE refresh_tokens
SET session_family_id = gen_random_uuid()
WHERE session_family_id IS NULL;

ALTER TABLE refresh_tokens
    ALTER COLUMN session_family_id SET NOT NULL;

CREATE INDEX idx_refresh_tokens_family_active
    ON refresh_tokens (session_family_id, is_revoked);
-- noviis:migration-phase expand
