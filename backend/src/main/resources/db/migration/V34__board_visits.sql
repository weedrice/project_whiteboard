CREATE TABLE board_visits (
    user_id BIGINT NOT NULL,
    board_id BIGINT NOT NULL,
    last_visited_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_board_visits PRIMARY KEY (user_id, board_id),
    CONSTRAINT fk_board_visits_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_board_visits_board FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE
);

CREATE INDEX idx_board_visits_user_modified ON board_visits (user_id, modified_at DESC);
CREATE INDEX idx_board_visits_board_user ON board_visits (board_id, user_id);
