package com.weedrice.whiteboard.domain.file.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileSchemaInitializer implements ApplicationRunner {

    private static final String TABLE_NAME = "files";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        synchronizeFilesTable();
    }

    void synchronizeFilesTable() {
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection, TABLE_NAME)) {
                return;
            }

            boolean migrated = false;

            if (!columnExists(connection, TABLE_NAME, "storage_status")) {
                jdbcTemplate.execute("ALTER TABLE files ADD COLUMN storage_status VARCHAR(30)");
                migrated = true;
            }
            jdbcTemplate.update("UPDATE files SET storage_status = 'ACTIVE' WHERE storage_status IS NULL");
            jdbcTemplate.execute("ALTER TABLE files ALTER COLUMN storage_status SET DEFAULT 'ACTIVE'");
            jdbcTemplate.execute("ALTER TABLE files ALTER COLUMN storage_status SET NOT NULL");

            if (!columnExists(connection, TABLE_NAME, "delete_requested_at")) {
                jdbcTemplate.execute("ALTER TABLE files ADD COLUMN delete_requested_at TIMESTAMP");
                migrated = true;
            }

            if (!columnExists(connection, TABLE_NAME, "delete_retry_count")) {
                jdbcTemplate.execute("ALTER TABLE files ADD COLUMN delete_retry_count INTEGER");
                migrated = true;
            }
            jdbcTemplate.update("UPDATE files SET delete_retry_count = 0 WHERE delete_retry_count IS NULL");
            jdbcTemplate.execute("ALTER TABLE files ALTER COLUMN delete_retry_count SET DEFAULT 0");
            jdbcTemplate.execute("ALTER TABLE files ALTER COLUMN delete_retry_count SET NOT NULL");

            if (!columnExists(connection, TABLE_NAME, "delete_last_error")) {
                jdbcTemplate.execute("ALTER TABLE files ADD COLUMN delete_last_error VARCHAR(1000)");
                migrated = true;
            }

            if (migrated) {
                log.info("Aligned legacy files table schema with deletion tracking columns");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to align files table schema", e);
        }
    }

    protected boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        return metadataExists(metaData.getTables(connection.getCatalog(), null, tableName, new String[]{"TABLE"}))
                || metadataExists(metaData.getTables(connection.getCatalog(), null, tableName.toUpperCase(), new String[]{"TABLE"}));
    }

    protected boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        return metadataExists(metaData.getColumns(connection.getCatalog(), null, tableName, columnName))
                || metadataExists(metaData.getColumns(connection.getCatalog(), null, tableName.toUpperCase(), columnName.toUpperCase()));
    }

    private boolean metadataExists(ResultSet resultSet) throws SQLException {
        try (ResultSet rs = resultSet) {
            return rs.next();
        }
    }
}
