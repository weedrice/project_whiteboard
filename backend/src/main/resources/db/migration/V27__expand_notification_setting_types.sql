ALTER TABLE user_notification_settings
    DROP CONSTRAINT IF EXISTS user_notification_settings_notification_type_check;

ALTER TABLE user_notification_settings
    DROP CONSTRAINT IF EXISTS ck_user_notification_settings_type;

ALTER TABLE user_notification_settings
    ADD CONSTRAINT ck_user_notification_settings_type
        CHECK (notification_type IN ('LIKE', 'COMMENT', 'REPLY', 'MENTION', 'SYSTEM', 'SANCTION'));
