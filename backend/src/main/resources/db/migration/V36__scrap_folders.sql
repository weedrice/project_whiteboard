CREATE TABLE scrap_folders (
    folder_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(60) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_scrap_folders_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT uk_scrap_folders_user_name UNIQUE (user_id, name)
);

ALTER TABLE scraps
    ADD COLUMN folder_id BIGINT NULL;

ALTER TABLE scraps
    ADD CONSTRAINT fk_scraps_folder FOREIGN KEY (folder_id) REFERENCES scrap_folders(folder_id) ON DELETE SET NULL;

CREATE INDEX idx_scrap_folders_user_sort ON scrap_folders(user_id, sort_order, folder_id);
CREATE INDEX idx_scraps_user_folder_created ON scraps(user_id, folder_id, created_at DESC);
