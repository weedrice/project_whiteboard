package com.weedrice.whiteboard.global.log.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorLogRetentionPropertiesTest {

    @Test
    @DisplayName("운영 환경변수 이름으로 에러 로그 보존 정책을 재정의한다")
    void bind_overridesRetentionPolicyWithDocumentedEnvironmentVariables() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> propertySources = loader.load(
                "application",
                new FileSystemResource("src/main/resources/application.yml"));

        assertThat(propertySources).isNotEmpty();
        assertThat(propertySources)
                .extracting(source -> source.getProperty("app.error-log.client-retention-days"))
                .contains("${APP_ERROR_LOG_CLIENT_RETENTION_DAYS:30}");
        assertThat(propertySources)
                .extracting(source -> source.getProperty("app.error-log.resolved-retention-days"))
                .contains("${APP_ERROR_LOG_RESOLVED_RETENTION_DAYS:90}");
        assertThat(propertySources)
                .extracting(source -> source.getProperty("app.error-log.cleanup-batch-size"))
                .contains("${APP_ERROR_LOG_CLEANUP_BATCH_SIZE:500}");
    }
}
