-- migration-phase: expand
CREATE TABLE domain_locks (
    lock_name VARCHAR(100) PRIMARY KEY
);

INSERT INTO domain_locks (lock_name)
VALUES ('BOARD_ORDER')
ON CONFLICT (lock_name) DO NOTHING;
