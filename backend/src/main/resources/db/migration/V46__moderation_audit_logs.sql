CREATE TABLE moderation_audit_logs (
    audit_id BIGSERIAL PRIMARY KEY,
    actor_type VARCHAR(20) NOT NULL,
    actor_user_id BIGINT,
    admin_id BIGINT,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT NOT NULL,
    board_id BIGINT,
    reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_moderation_audit_actor_user
        FOREIGN KEY (actor_user_id) REFERENCES users(user_id),
    CONSTRAINT fk_moderation_audit_board
        FOREIGN KEY (board_id) REFERENCES boards(board_id)
);

CREATE INDEX idx_moderation_audits_board_created
    ON moderation_audit_logs (board_id, created_at DESC);

CREATE INDEX idx_moderation_audits_actor_created
    ON moderation_audit_logs (actor_user_id, created_at DESC);

CREATE INDEX idx_moderation_audits_action_created
    ON moderation_audit_logs (action, created_at DESC);
