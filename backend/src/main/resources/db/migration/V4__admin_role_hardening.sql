-- Imported from docs/sql/admin-role-hardening-postgres.sql.
-- Flyway manages the transaction boundary for this migration.

-- Manual PostgreSQL constraint update for admin role hardening.
-- Run after taking a backup and before deploying the stage-2 admin changes.

-- Audit rows that violate the new runtime contract before adding the constraint.


ALTER TABLE admins
    DROP CONSTRAINT IF EXISTS chk_admins_role_allowed;

ALTER TABLE admins
    ADD CONSTRAINT chk_admins_role_allowed
        CHECK (role IN ('BOARD_ADMIN', 'MODERATOR'));
