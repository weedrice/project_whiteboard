ALTER TABLE verification_codes
    ADD COLUMN failed_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE verification_codes
    ADD CONSTRAINT chk_verification_codes_failed_attempts
        CHECK (failed_attempts BETWEEN 0 AND 5);
