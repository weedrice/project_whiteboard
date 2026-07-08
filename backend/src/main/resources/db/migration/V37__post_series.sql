CREATE TABLE post_series (
    series_id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_post_series_owner FOREIGN KEY (owner_user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE post_series_items (
    item_id BIGSERIAL PRIMARY KEY,
    series_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_post_series_items_series FOREIGN KEY (series_id) REFERENCES post_series(series_id) ON DELETE CASCADE,
    CONSTRAINT fk_post_series_items_post FOREIGN KEY (post_id) REFERENCES posts(post_id) ON DELETE CASCADE,
    CONSTRAINT uk_post_series_items_post UNIQUE (post_id)
);

CREATE INDEX idx_post_series_owner ON post_series(owner_user_id, series_id DESC);
CREATE INDEX idx_post_series_items_series_sort ON post_series_items(series_id, sort_order, item_id);
