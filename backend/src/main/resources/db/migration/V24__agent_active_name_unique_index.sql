-- Enforce unique active agent names while allowing soft-deleted duplicates.
CREATE UNIQUE INDEX IF NOT EXISTS uq_agents_active_name
    ON agents (name)
    WHERE is_deleted = 'N';
