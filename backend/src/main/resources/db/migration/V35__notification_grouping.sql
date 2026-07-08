ALTER TABLE notifications
    ADD COLUMN group_key VARCHAR(160),
    ADD COLUMN group_count INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN last_event_at TIMESTAMP;

UPDATE notifications
SET group_key = CONCAT(user_id, ':', notification_type, ':', source_type, ':', COALESCE(source_id::TEXT, '')),
    last_event_at = created_at
WHERE group_key IS NULL;

ALTER TABLE notifications
    ALTER COLUMN last_event_at SET NOT NULL;

CREATE INDEX idx_notifications_user_group_unread
    ON notifications (user_id, group_key, is_read, last_event_at DESC);
