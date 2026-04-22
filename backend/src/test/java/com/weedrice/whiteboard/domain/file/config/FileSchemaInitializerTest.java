package com.weedrice.whiteboard.domain.file.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileSchemaInitializerTest {

    @Mock
    private DataSource dataSource;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private Connection connection;

    @Test
    @DisplayName("files 테이블이 없으면 스키마 동기화를 건너뛴다")
    void synchronizeFilesTable_skipsWhenTableMissing() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);

        FileSchemaInitializer initializer = new TestFileSchemaInitializer(dataSource, jdbcTemplate, false, Set.of());

        initializer.synchronizeFilesTable();

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("레거시 files 테이블에 삭제 추적 컬럼들을 추가하고 기본값을 보정한다")
    void synchronizeFilesTable_addsMissingLegacyColumns() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);

        FileSchemaInitializer initializer = new TestFileSchemaInitializer(
                dataSource,
                jdbcTemplate,
                true,
                Set.of("storage_status", "delete_requested_at", "delete_retry_count", "delete_last_error")
        );

        initializer.synchronizeFilesTable();

        InOrder inOrder = inOrder(jdbcTemplate);
        inOrder.verify(jdbcTemplate).execute("ALTER TABLE files ADD COLUMN storage_status VARCHAR(30)");
        inOrder.verify(jdbcTemplate).update("UPDATE files SET storage_status = 'ACTIVE' WHERE storage_status IS NULL");
        inOrder.verify(jdbcTemplate).execute("ALTER TABLE files ALTER COLUMN storage_status SET DEFAULT 'ACTIVE'");
        inOrder.verify(jdbcTemplate).execute("ALTER TABLE files ALTER COLUMN storage_status SET NOT NULL");
        inOrder.verify(jdbcTemplate).execute("ALTER TABLE files ADD COLUMN delete_requested_at TIMESTAMP");
        inOrder.verify(jdbcTemplate).execute("ALTER TABLE files ADD COLUMN delete_retry_count INTEGER");
        inOrder.verify(jdbcTemplate).update("UPDATE files SET delete_retry_count = 0 WHERE delete_retry_count IS NULL");
        inOrder.verify(jdbcTemplate).execute("ALTER TABLE files ALTER COLUMN delete_retry_count SET DEFAULT 0");
        inOrder.verify(jdbcTemplate).execute("ALTER TABLE files ALTER COLUMN delete_retry_count SET NOT NULL");
        inOrder.verify(jdbcTemplate).execute("ALTER TABLE files ADD COLUMN delete_last_error VARCHAR(1000)");
    }

    @Test
    @DisplayName("이미 최신 스키마면 컬럼 추가는 하지 않는다")
    void synchronizeFilesTable_doesNotAddExistingColumns() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);

        FileSchemaInitializer initializer = new TestFileSchemaInitializer(dataSource, jdbcTemplate, true, Set.of());

        initializer.synchronizeFilesTable();

        org.mockito.Mockito.verify(jdbcTemplate, never()).execute("ALTER TABLE files ADD COLUMN storage_status VARCHAR(30)");
        org.mockito.Mockito.verify(jdbcTemplate, never()).execute("ALTER TABLE files ADD COLUMN delete_requested_at TIMESTAMP");
        org.mockito.Mockito.verify(jdbcTemplate, never()).execute("ALTER TABLE files ADD COLUMN delete_retry_count INTEGER");
        org.mockito.Mockito.verify(jdbcTemplate, never()).execute("ALTER TABLE files ADD COLUMN delete_last_error VARCHAR(1000)");
    }

    private static class TestFileSchemaInitializer extends FileSchemaInitializer {

        private final boolean tableExists;
        private final Set<String> missingColumns;

        private TestFileSchemaInitializer(DataSource dataSource, JdbcTemplate jdbcTemplate, boolean tableExists,
                Set<String> missingColumns) {
            super(dataSource, jdbcTemplate);
            this.tableExists = tableExists;
            this.missingColumns = missingColumns;
        }

        @Override
        protected boolean tableExists(Connection connection, String tableName) {
            return tableExists;
        }

        @Override
        protected boolean columnExists(Connection connection, String tableName, String columnName) {
            return !missingColumns.contains(columnName);
        }
    }
}
