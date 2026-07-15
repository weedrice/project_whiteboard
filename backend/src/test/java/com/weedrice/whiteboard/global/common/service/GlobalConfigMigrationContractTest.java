package com.weedrice.whiteboard.global.common.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalConfigMigrationContractTest {

    @Test
    void corePointConfigMigrationSeedsFallbackValuesWithoutOverwritingExistingRows() throws IOException {
        String migration = new ClassPathResource(
                "db/migration/V53__harden_global_config_contracts.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("('POINT_SIGNUP_BONUS', '500'")
                .contains("('POINT_BOARD_CREATE_COST', '500'")
                .contains("('POINT_POST_CREATE_REWARD', '50'")
                .contains("('POINT_COMMENT_CREATE_REWARD', '10'")
                .contains("ON CONFLICT (config_key) DO NOTHING")
                .contains("WHERE config_key = 'NOBICON_PRICE'")
                .contains("AND description = 'Default Noviicon purchase price'");
    }
}
