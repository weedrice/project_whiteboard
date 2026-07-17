-- migration-phase: contract
-- noviis:migration-phase contract
-- noviis:design-doc docs/design-notes/notification-unread-group-uniqueness-2026-07-17.md

WITH duplicate_unread_groups AS (
    SELECT notification_id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id, group_key
               ORDER BY last_event_at DESC, notification_id DESC
           ) AS duplicate_rank
    FROM notifications
    WHERE is_read = 'N' AND group_key IS NOT NULL
)
UPDATE notifications
SET is_read = 'Y'
WHERE notification_id IN (
    SELECT notification_id FROM duplicate_unread_groups WHERE duplicate_rank > 1
);

CREATE UNIQUE INDEX uk_notifications_unread_group
    ON notifications (user_id, group_key)
    WHERE is_read = 'N' AND group_key IS NOT NULL;
