-- noviis:migration-phase contract
-- noviis:design-doc docs/design-notes/password-reset-token-retention-2026-07-17.md

ALTER TABLE password_reset_tokens
    DROP CONSTRAINT IF EXISTS fk_password_reset_tokens_verification;

ALTER TABLE password_reset_tokens
    ADD CONSTRAINT fk_password_reset_tokens_verification
        FOREIGN KEY (verification_id)
            REFERENCES verification_codes (verification_id)
            ON DELETE SET NULL;
