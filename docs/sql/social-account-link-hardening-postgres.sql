-- Manual PostgreSQL hardening for social account link uniqueness.
-- Run after taking a backup and before deploying the social account link hardening code.

SELECT provider, provider_id, COUNT(*) AS duplicate_count
FROM social_accounts
GROUP BY provider, provider_id
HAVING COUNT(*) > 1;

SELECT user_id, provider, COUNT(*) AS duplicate_count
FROM social_accounts
GROUP BY user_id, provider
HAVING COUNT(*) > 1;

BEGIN;

LOCK TABLE social_accounts IN SHARE ROW EXCLUSIVE MODE;

WITH ranked_provider_links AS (
    SELECT
        social_account_id,
        ROW_NUMBER() OVER (
            PARTITION BY provider, provider_id
            ORDER BY social_account_id ASC
        ) AS row_num
    FROM social_accounts
),
deleted_provider_duplicates AS (
    DELETE FROM social_accounts social_account
    USING ranked_provider_links ranked
    WHERE social_account.social_account_id = ranked.social_account_id
      AND ranked.row_num > 1
    RETURNING 1
)
SELECT COUNT(*) AS provider_duplicates_deleted
FROM deleted_provider_duplicates;

WITH ranked_user_provider_links AS (
    SELECT
        social_account_id,
        ROW_NUMBER() OVER (
            PARTITION BY user_id, provider
            ORDER BY social_account_id ASC
        ) AS row_num
    FROM social_accounts
),
deleted_user_provider_duplicates AS (
    DELETE FROM social_accounts social_account
    USING ranked_user_provider_links ranked
    WHERE social_account.social_account_id = ranked.social_account_id
      AND ranked.row_num > 1
    RETURNING 1
)
SELECT COUNT(*) AS user_provider_duplicates_deleted
FROM deleted_user_provider_duplicates;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_social_accounts_provider_provider_id'
    ) THEN
        ALTER TABLE social_accounts
            ADD CONSTRAINT uk_social_accounts_provider_provider_id
            UNIQUE (provider, provider_id);
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_social_accounts_user_provider'
    ) THEN
        ALTER TABLE social_accounts
            ADD CONSTRAINT uk_social_accounts_user_provider
            UNIQUE (user_id, provider);
    END IF;
END
$$;

COMMIT;
