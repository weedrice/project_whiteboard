-- Normalize email identifiers to the application canonical form.
-- Flyway manages the transaction boundary for this migration.

LOCK TABLE users IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE verification_codes IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT LOWER(BTRIM(email)) AS canonical_email
            FROM users
            GROUP BY LOWER(BTRIM(email))
            HAVING COUNT(*) > 1
        ) duplicates
    ) THEN
        RAISE EXCEPTION 'Cannot canonicalize users.email because duplicate canonical emails exist';
    END IF;
END
$$;

UPDATE users
SET email = LOWER(BTRIM(email))
WHERE email IS DISTINCT FROM LOWER(BTRIM(email));

UPDATE verification_codes
SET email = LOWER(BTRIM(email))
WHERE email IS DISTINCT FROM LOWER(BTRIM(email));

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_canonical
    ON users (LOWER(BTRIM(email)));
