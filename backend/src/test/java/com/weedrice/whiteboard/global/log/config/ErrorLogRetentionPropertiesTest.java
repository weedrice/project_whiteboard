package com.weedrice.whiteboard.global.log.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorLogRetentionPropertiesTest {

    @Test
    @DisplayName("운영 환경변수 이름으로 에러 로그 보존 정책을 재정의한다")
    void bind_overridesRetentionPolicyWithDocumentedEnvironmentVariables() throws IOException {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("APP_ERROR_LOG_CLIENT_RETENTION_DAYS", "7")
                .withProperty("APP_ERROR_LOG_RESOLVED_RETENTION_DAYS", "45")
                .withProperty("APP_ERROR_LOG_CLEANUP_BATCH_SIZE", "123");
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load("application", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);

        ErrorLogRetentionProperties properties = Binder.get(environment)
                .bind("app.error-log", Bindable.of(ErrorLogRetentionProperties.class))
                .get();

        assertThat(properties.getClientRetentionDays()).isEqualTo(7);
        assertThat(properties.getResolvedRetentionDays()).isEqualTo(45);
        assertThat(properties.getCleanupBatchSize()).isEqualTo(123);
    }
}
