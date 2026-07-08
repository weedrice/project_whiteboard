CREATE TABLE polls (
    poll_id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    question VARCHAR(200) NOT NULL,
    multiple_choice_enabled VARCHAR(1) NOT NULL DEFAULT 'N',
    anonymous_enabled VARCHAR(1) NOT NULL DEFAULT 'N',
    closes_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_polls_post FOREIGN KEY (post_id) REFERENCES posts(post_id) ON DELETE CASCADE,
    CONSTRAINT uk_polls_post UNIQUE (post_id)
);

CREATE TABLE poll_options (
    option_id BIGSERIAL PRIMARY KEY,
    poll_id BIGINT NOT NULL,
    option_text VARCHAR(100) NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_poll_options_poll FOREIGN KEY (poll_id) REFERENCES polls(poll_id) ON DELETE CASCADE,
    CONSTRAINT uk_poll_options_poll_sort UNIQUE (poll_id, sort_order)
);

CREATE TABLE poll_votes (
    vote_id BIGSERIAL PRIMARY KEY,
    poll_id BIGINT NOT NULL,
    option_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_poll_votes_poll FOREIGN KEY (poll_id) REFERENCES polls(poll_id) ON DELETE CASCADE,
    CONSTRAINT fk_poll_votes_option FOREIGN KEY (option_id) REFERENCES poll_options(option_id) ON DELETE CASCADE,
    CONSTRAINT fk_poll_votes_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT uk_poll_votes_single_choice UNIQUE (poll_id, user_id)
);

CREATE INDEX idx_poll_options_poll ON poll_options(poll_id);
CREATE INDEX idx_poll_votes_poll ON poll_votes(poll_id);
CREATE INDEX idx_poll_votes_user ON poll_votes(user_id);
