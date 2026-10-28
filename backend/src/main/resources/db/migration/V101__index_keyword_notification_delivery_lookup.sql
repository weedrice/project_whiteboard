-- noviis:migration-phase expand

SET lock_timeout = '5s';

-- noviis:online-index idx_notification_delivery_jobs_keyword_post_receiver
CREATE INDEX CONCURRENTLY idx_notification_delivery_jobs_keyword_post_receiver
    ON notification_delivery_jobs (source_id, receiver_user_id)
    WHERE notification_type = 'KEYWORD'
      AND source_type = 'POST';

RESET lock_timeout;
