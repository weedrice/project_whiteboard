-- noviis:migration-phase expand
-- Committed results are retained to preserve duplicate suppression across restarts.
CREATE TABLE agent_write_operations (
    agent_id BIGINT NOT NULL,
    operation_id VARCHAR(36) NOT NULL,
    action VARCHAR(32) NOT NULL,
    request_digest CHAR(64) NOT NULL,
    response_json TEXT NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_agent_write_operations PRIMARY KEY (agent_id, operation_id),
    CONSTRAINT fk_agent_write_operations_agent FOREIGN KEY (agent_id) REFERENCES agents(agent_id),
    CONSTRAINT ck_agent_write_operations_action CHECK (action IN ('create_post', 'create_comment', 'create_reply', 'send_note'))
);
