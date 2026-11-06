-- noviis:migration-phase expand
-- Nullable timing fields preserve compatibility with the previous application.
SET LOCAL lock_timeout = '5s';
ALTER TABLE semantic_search_jobs
    ADD COLUMN queued_at TIMESTAMP(6),
    ADD COLUMN next_attempt_at TIMESTAMP(6);
