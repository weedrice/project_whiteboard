-- Temporary tables created by older migrations can survive when Flyway uses a pooled connection.
DROP TABLE IF EXISTS pg_temp.legacy_scheduled_post_file_refs;
DROP TABLE IF EXISTS pg_temp.legacy_scheduled_post_file_refs_raw;
