package com.weedrice.whiteboard;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class ProfilePrivacyMigrationContractTest {

    @Test
    void existingRowsAndLegacyInsertsRemainPublic() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V100__profile_privacy_settings.sql"), StandardCharsets.UTF_8);
        assertThat(sql).contains("-- noviis:migration-phase expand");
        // H2 verifies additive/default semantics; PostgreSQL smoke verification is separate.
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:profile_privacy_migration;MODE=PostgreSQL")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE user_settings (user_id BIGINT PRIMARY KEY, theme VARCHAR(20))");
                statement.execute("INSERT INTO user_settings (user_id, theme) VALUES (1, 'LIGHT')");
                // H2 accepts one ADD COLUMN per statement, unlike PostgreSQL's comma-separated clauses.
                for (String clause : sql.substring(sql.indexOf("ADD COLUMN")).replace(";", "").split(",")) {
                    statement.execute("ALTER TABLE user_settings " + clause.strip());
                }
                statement.execute("INSERT INTO user_settings (user_id, theme) VALUES (2, 'DARK')");
                try (var rows = statement.executeQuery("SELECT * FROM user_settings ORDER BY user_id")) {
                    for (int id = 1; id <= 2; id++) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getLong("user_id")).isEqualTo(id);
                        assertThat(rows.getString("profile_overview_public")).isEqualTo("Y");
                        assertThat(rows.getString("profile_posts_public")).isEqualTo("Y");
                        assertThat(rows.getString("profile_comments_public")).isEqualTo("Y");
                        assertThat(rows.getString("theme")).isEqualTo(id == 1 ? "LIGHT" : "DARK");
                    }
                    assertThat(rows.next()).isFalse();
                }
            }
        }
    }
}
