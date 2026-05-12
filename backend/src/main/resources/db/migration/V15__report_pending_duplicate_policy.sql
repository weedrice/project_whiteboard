-- Flyway manages the transaction boundary for this migration.

LOCK TABLE reports IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM reports
        WHERE status = 'PENDING'
        GROUP BY reporter_id, target_type, target_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate pending reports exist for reporter_id, target_type, target_id';
    END IF;
END
$$;

ALTER TABLE reports
    DROP CONSTRAINT IF EXISTS uk_reports_user_target;

CREATE UNIQUE INDEX IF NOT EXISTS uq_reports_pending_user_target
    ON reports (reporter_id, target_type, target_id)
    WHERE status = 'PENDING';
