DO $$
DECLARE
    storage_status_constraint_name text;
BEGIN
    SELECT con.conname
      INTO storage_status_constraint_name
      FROM pg_constraint con
      JOIN pg_class rel ON rel.oid = con.conrelid
      JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
     WHERE rel.relname = 'files'
       AND nsp.nspname = current_schema()
       AND con.contype = 'c'
       AND pg_get_constraintdef(con.oid) LIKE '%storage_status%'
     LIMIT 1;

    IF storage_status_constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE files DROP CONSTRAINT %I', storage_status_constraint_name);
    END IF;
END $$;

ALTER TABLE files
    ADD CONSTRAINT chk_files_storage_status
    CHECK (storage_status IN ('PENDING_UPLOAD', 'ACTIVE', 'PENDING_DELETE', 'DELETING', 'DELETE_FAILED'));
