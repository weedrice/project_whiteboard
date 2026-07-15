ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS message_key VARCHAR(160);

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS message_params TEXT;
