-- noviis:migration-phase expand

INSERT INTO domain_locks (lock_name)
VALUES ('POST_DRAFT_CLEANUP');
