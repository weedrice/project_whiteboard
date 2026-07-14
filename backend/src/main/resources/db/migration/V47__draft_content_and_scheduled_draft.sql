ALTER TABLE draft_posts
    ADD COLUMN poll_json TEXT;

ALTER TABLE draft_posts
    ADD COLUMN series_id BIGINT;

ALTER TABLE draft_posts
    ADD CONSTRAINT fk_draft_posts_series
        FOREIGN KEY (series_id) REFERENCES post_series(series_id) ON DELETE SET NULL;

ALTER TABLE scheduled_posts
    ADD COLUMN draft_id BIGINT;

ALTER TABLE scheduled_posts
    ADD CONSTRAINT fk_scheduled_posts_draft
        FOREIGN KEY (draft_id) REFERENCES draft_posts(draft_id) ON DELETE SET NULL;

CREATE UNIQUE INDEX uk_scheduled_posts_draft
    ON scheduled_posts (draft_id);
