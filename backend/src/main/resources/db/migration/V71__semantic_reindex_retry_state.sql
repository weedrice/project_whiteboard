-- noviis:migration-phase contract
-- noviis:design-doc docs/design-notes/semantic-reindex-retry-state-2026-07-17.md

ALTER TABLE semantic_search_reindex_jobs
    ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN last_error VARCHAR(500),
    ADD COLUMN lease_token UUID,
    ADD COLUMN failed_at TIMESTAMP(6),
    ADD CONSTRAINT ck_semantic_search_reindex_retry_count
        CHECK (retry_count BETWEEN 0 AND 5);

ALTER TABLE semantic_search_reindex_jobs
    DROP CONSTRAINT ck_semantic_search_reindex_status;

ALTER TABLE semantic_search_reindex_jobs
    ADD CONSTRAINT ck_semantic_search_reindex_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'));

CREATE INDEX idx_semantic_search_reindex_runnable
    ON semantic_search_reindex_jobs (status, next_attempt_at, updated_at, reindex_job_id);
