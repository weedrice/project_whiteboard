-- noviis:migration-phase expand
CREATE TABLE view_history_sessions (
    view_history_id BIGINT NOT NULL REFERENCES view_histories(view_history_id) ON DELETE CASCADE,
    session_id UUID NOT NULL,
    duration_ms BIGINT NOT NULL CHECK (duration_ms >= 0),
    PRIMARY KEY (view_history_id, session_id)
);
