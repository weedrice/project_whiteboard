-- noviis:migration-phase expand

SET lock_timeout = '5s';

-- noviis:online-index idx_point_histories_user_target_type
CREATE INDEX CONCURRENTLY idx_point_histories_user_target_type
    ON point_histories (user_id, related_type, related_id, type);

RESET lock_timeout;
