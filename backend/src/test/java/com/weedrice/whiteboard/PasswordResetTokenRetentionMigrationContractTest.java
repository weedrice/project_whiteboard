package com.weedrice.whiteboard;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenRetentionMigrationContractTest {

    @Test
    void v70ReplacesVerificationForeignKeyWithSetNullContract() throws IOException {
        String sql;
        try (var input = getClass().getClassLoader().getResourceAsStream(
                "db/migration/V70__password_reset_token_retention.sql")) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("-- noviis:migration-phase contract")
                .contains("DROP CONSTRAINT IF EXISTS fk_password_reset_tokens_verification")
                .contains("ON DELETE SET NULL");
    }
}
