-- noviis:migration-phase contract
-- noviis:design-doc docs/design-notes/inquiry-module-v91.md

SET lock_timeout = '5s';

CREATE TABLE inquiries (
    inquiry_id BIGSERIAL PRIMARY KEY,
    author_user_id BIGINT NOT NULL REFERENCES users(user_id),
    category VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL,
    staff_action_since TIMESTAMP,
    first_responded_at TIMESTAMP,
    resolved_at TIMESTAMP,
    closed_at TIMESTAMP,
    closed_by_user_id BIGINT REFERENCES users(user_id),
    closure_reason VARCHAR(30),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_public_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_inquiries_category CHECK (category IN (
        'ACCOUNT', 'SERVICE_USE', 'TECHNICAL', 'CONTENT_OPERATION', 'SUGGESTION', 'OTHER'
    )),
    CONSTRAINT ck_inquiries_status CHECK (status IN ('NEW', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    CONSTRAINT ck_inquiries_closure_reason CHECK (
        closure_reason IS NULL OR closure_reason IN ('WITHDRAWN', 'USER_CONFIRMED', 'ADMIN_CLOSED', 'AUTO_CLOSED')
    )
);

CREATE INDEX idx_inquiries_author_created
    ON inquiries (author_user_id, created_at DESC);
CREATE INDEX idx_inquiries_queue
    ON inquiries (status, staff_action_since, created_at);
CREATE INDEX idx_inquiries_category_status
    ON inquiries (category, status, created_at DESC);
CREATE INDEX idx_inquiries_author_status
    ON inquiries (author_user_id, status, inquiry_id);
CREATE INDEX idx_inquiries_auto_close
    ON inquiries (resolved_at, inquiry_id)
    WHERE status = 'RESOLVED';

CREATE TABLE inquiry_messages (
    message_id BIGSERIAL PRIMARY KEY,
    inquiry_id BIGINT NOT NULL REFERENCES inquiries(inquiry_id) ON DELETE CASCADE,
    author_user_id BIGINT NOT NULL REFERENCES users(user_id),
    message_type VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_inquiry_messages_type CHECK (
        message_type IN ('USER_MESSAGE', 'STAFF_REPLY', 'INTERNAL_NOTE')
    )
);

CREATE INDEX idx_inquiry_messages_inquiry_created
    ON inquiry_messages (inquiry_id, created_at, message_id);

CREATE TABLE inquiry_histories (
    history_id BIGSERIAL PRIMARY KEY,
    inquiry_id BIGINT NOT NULL REFERENCES inquiries(inquiry_id) ON DELETE CASCADE,
    actor_user_id BIGINT REFERENCES users(user_id),
    action_type VARCHAR(40) NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inquiry_histories_inquiry_created
    ON inquiry_histories (inquiry_id, created_at, history_id);

INSERT INTO domain_locks (lock_name)
VALUES ('INQUIRY_AUTO_CLOSE')
ON CONFLICT (lock_name) DO NOTHING;

INSERT INTO global_configs (config_key, config_value, description, created_at, modified_at)
VALUES
    ('INQUIRY_PRIORITY_HIGH_HOURS', '24', 'Normal inquiry escalation threshold in hours', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('INQUIRY_PRIORITY_URGENT_HOURS', '72', 'Normal inquiry urgent threshold in hours', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('INQUIRY_PRIORITY_HIGH_CATEGORY_URGENT_HOURS', '24', 'High-category inquiry urgent threshold in hours', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('INQUIRY_NOTIFICATION_TYPE_ENABLED', 'N', 'Enables persistence of the dedicated INQUIRY notification enum after the rollback window', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('INQUIRY_LEGACY_WRITE_ENABLED', 'Y', 'Allows legacy inquiry-board writes during staged rollout', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (config_key) DO NOTHING;

ALTER TABLE user_notification_settings
    DROP CONSTRAINT IF EXISTS user_notification_settings_notification_type_check;
ALTER TABLE user_notification_settings
    ADD CONSTRAINT user_notification_settings_notification_type_check
        CHECK (notification_type IN (
            'LIKE', 'COMMENT', 'REPLY', 'MENTION', 'MESSAGE', 'SYSTEM', 'SANCTION', 'KEYWORD', 'BADGE', 'INQUIRY'
        ));

RESET lock_timeout;
