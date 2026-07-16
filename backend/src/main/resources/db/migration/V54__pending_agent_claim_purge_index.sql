CREATE INDEX idx_agents_pending_claim_purge
    ON agents (modified_at, agent_id)
    WHERE status = 'PENDING_CLAIM'
      AND is_deleted = 'Y'
      AND user_id IS NULL
      AND claimed_at IS NULL;
