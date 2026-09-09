-- noviis:migration-phase expand

SET lock_timeout = '5s';

-- noviis:online-index uk_agents_agent_owner
CREATE UNIQUE INDEX CONCURRENTLY uk_agents_agent_owner
    ON agents (agent_id, user_id);

RESET lock_timeout;
