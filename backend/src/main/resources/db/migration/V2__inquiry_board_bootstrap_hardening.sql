-- Imported from docs/sql/inquiry-board-bootstrap-hardening-postgres.sql.
-- Flyway manages the transaction boundary for this migration.

-- Manual PostgreSQL cleanup and constraint hardening for inquiry board bootstrap.
-- Run after taking a backup and before relying on the new runtime safeguards.


-- Keep only one active category row per (board, name).
WITH ranked_categories AS (
    SELECT
        category_id,
        ROW_NUMBER() OVER (
            PARTITION BY board_id, name
            ORDER BY sort_order ASC, category_id ASC
        ) AS row_num
    FROM board_categories
    WHERE is_active = 'Y'
)
UPDATE board_categories bc
SET is_active = 'N'
FROM ranked_categories rc
WHERE bc.category_id = rc.category_id
  AND rc.row_num > 1;

-- Keep only one active admin row per (user, board, role).
WITH ranked_admins_by_user AS (
    SELECT
        admin_id,
        ROW_NUMBER() OVER (
            PARTITION BY user_id, board_id, role
            ORDER BY admin_id DESC
        ) AS row_num
    FROM admins
    WHERE is_active = 'Y'
)
UPDATE admins a
SET is_active = 'N'
FROM ranked_admins_by_user rau
WHERE a.admin_id = rau.admin_id
  AND rau.row_num > 1;

-- Keep only one active BOARD_ADMIN per board.
WITH ranked_admins_by_board AS (
    SELECT
        admin_id,
        ROW_NUMBER() OVER (
            PARTITION BY board_id, role
            ORDER BY admin_id DESC
        ) AS row_num
    FROM admins
    WHERE is_active = 'Y'
      AND role = 'BOARD_ADMIN'
)
UPDATE admins a
SET is_active = 'N'
FROM ranked_admins_by_board rab
WHERE a.admin_id = rab.admin_id
  AND rab.row_num > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_board_categories_active_name
    ON board_categories (board_id, name)
    WHERE is_active = 'Y';

CREATE UNIQUE INDEX IF NOT EXISTS uq_admins_active_user_board_role
    ON admins (user_id, board_id, role)
    WHERE is_active = 'Y';

CREATE UNIQUE INDEX IF NOT EXISTS uq_admins_active_board_admin
    ON admins (board_id, role)
    WHERE is_active = 'Y'
      AND role = 'BOARD_ADMIN';
