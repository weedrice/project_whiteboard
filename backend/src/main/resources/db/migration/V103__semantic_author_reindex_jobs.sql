-- noviis:migration-phase expand

-- Both queues use the existing job ID namespace for administrative redrive.
CREATE TABLE semantic_search_author_reindex_jobs (
    reindex_job_id BIGINT PRIMARY KEY DEFAULT nextval('semantic_search_reindex_jobs_reindex_job_id_seq'::regclass),
    scope_type VARCHAR(30) NOT NULL CHECK (scope_type = 'AUTHOR'),
    scope_id BIGINT NOT NULL CHECK (scope_id > 0),
    content_phase VARCHAR(20) NOT NULL CHECK (content_phase IN ('POST', 'COMMENT')),
    last_content_id BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count BETWEEN 0 AND 5),
    next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_started_at TIMESTAMP(6),
    last_error VARCHAR(500),
    lease_token UUID,
    failed_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (scope_type, scope_id)
);

CREATE INDEX idx_semantic_author_reindex_runnable
    ON semantic_search_author_reindex_jobs (status, next_attempt_at, updated_at, reindex_job_id);
