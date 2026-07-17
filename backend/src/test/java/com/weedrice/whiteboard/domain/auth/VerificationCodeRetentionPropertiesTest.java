package com.weedrice.whiteboard.domain.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationCodeRetentionPropertiesTest {

    @Test
    void applicationConfigurationExposesRetentionAndBatchOverrides() throws IOException {
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader().load(
                "application",
                new FileSystemResource("src/main/resources/application.yml"));

        assertThat(propertySources)
                .extracting(source -> source.getProperty("app.verification-code.terminal-retention-days"))
                .contains("${APP_VERIFICATION_CODE_TERMINAL_RETENTION_DAYS:7}");
        assertThat(propertySources)
                .extracting(source -> source.getProperty("app.verification-code.cleanup-batch-size"))
                .contains("${APP_VERIFICATION_CODE_CLEANUP_BATCH_SIZE:500}");
        assertThat(propertySources)
                .extracting(source -> source.getProperty("app.verification-code.pending-recovery-grace-minutes"))
                .contains("${APP_VERIFICATION_CODE_PENDING_RECOVERY_GRACE_MINUTES:30}");
        assertThat(propertySources)
                .extracting(source -> source.getProperty("app.verification-code.pending-recovery-batch-size"))
                .contains("${APP_VERIFICATION_CODE_PENDING_RECOVERY_BATCH_SIZE:500}");
        assertThat(propertySources)
                .extracting(source -> source.getProperty("app.verification-code.pending-recovery-max-batches"))
                .contains("${APP_VERIFICATION_CODE_PENDING_RECOVERY_MAX_BATCHES:10}");
        assertThat(propertySources)
                .extracting(source -> source.getProperty("app.verification-code.password-reset-token-retention-days"))
                .contains("${APP_PASSWORD_RESET_TOKEN_RETENTION_DAYS:30}");
        assertThat(propertySources)
                .extracting(source -> source.getProperty("app.verification-code.password-reset-token-cleanup-batch-size"))
                .contains("${APP_PASSWORD_RESET_TOKEN_CLEANUP_BATCH_SIZE:500}");
    }
}
