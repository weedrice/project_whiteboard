-- Align legacy PostgreSQL databases where V5 created this column as CHAR(1).
-- Hibernate validates the VerificationCode BooleanToYN mapping as VARCHAR(1).

ALTER TABLE verification_codes
    ALTER COLUMN is_ticket_consumed TYPE VARCHAR(1)
    USING is_ticket_consumed::VARCHAR(1);
