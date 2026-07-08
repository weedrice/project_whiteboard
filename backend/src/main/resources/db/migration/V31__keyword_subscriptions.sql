ALTER TABLE user_notification_settings
    DROP CONSTRAINT IF EXISTS user_notification_settings_notification_type_check;

ALTER TABLE user_notification_settings
    DROP CONSTRAINT IF EXISTS ck_user_notification_settings_type;

ALTER TABLE user_notification_settings
    ADD CONSTRAINT user_notification_settings_notification_type_check
        CHECK (notification_type IN ('LIKE', 'COMMENT', 'REPLY', 'MENTION', 'MESSAGE', 'SYSTEM', 'SANCTION', 'KEYWORD'));

CREATE TABLE user_keyword_subscriptions (
    subscription_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    keyword VARCHAR(50) NOT NULL,
    last_notified_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_keyword_subscriptions_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT uk_user_keyword_subscriptions_user_keyword UNIQUE (user_id, keyword)
);

CREATE INDEX idx_user_keyword_subscriptions_keyword ON user_keyword_subscriptions(keyword);
CREATE INDEX idx_user_keyword_subscriptions_user ON user_keyword_subscriptions(user_id);
