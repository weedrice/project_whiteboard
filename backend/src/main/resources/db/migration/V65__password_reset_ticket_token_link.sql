-- migration-phase: expand
-- noviis:migration-phase expand

ALTER TABLE password_reset_tokens
    ADD COLUMN verification_id BIGINT;

ALTER TABLE password_reset_tokens
    ADD CONSTRAINT fk_password_reset_tokens_verification
        FOREIGN KEY (verification_id) REFERENCES verification_codes (verification_id),
    ADD CONSTRAINT uk_password_reset_tokens_verification UNIQUE (verification_id);
