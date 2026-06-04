package com.weedrice.whiteboard.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;

class EnvironmentValidatorTest {

    private static final List<String> REQUIRED_PROD_VARIABLES = List.of(
            "DB_HOST",
            "DB_NAME",
            "DB_USER",
            "DB_PASSWORD",
            "JWT_SECRET",
            "GITHUB_CLIENT_ID",
            "GITHUB_CLIENT_SECRET",
            "GOOGLE_CLIENT_ID",
            "GOOGLE_CLIENT_SECRET",
            "DISCORD_CLIENT_ID",
            "DISCORD_CLIENT_SECRET",
            "MAIL_USERNAME",
            "MAIL_APP_PASSWORD",
            "AWS_ACCESS_KEY",
            "AWS_SECRET_KEY",
            "AWS_S3_REGION",
            "S3_BUCKET",
            "FRONTEND_URL");

    @Test
    @DisplayName("skips validation outside the prod profile")
    void nonProdProfile_skipsValidation() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        EnvironmentValidator validator = new EnvironmentValidator(environment);

        assertThatCode(() -> validator.onApplicationEvent(createEvent()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("passes when every prod variable used by application-prod.yml exists")
    void prodProfile_validVariables_success() {
        MockEnvironment environment = prodEnvironmentExcept();
        EnvironmentValidator validator = new EnvironmentValidator(environment);

        assertThatCode(() -> validator.onApplicationEvent(createEvent()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("fails when SMTP credentials used by application-prod.yml are missing")
    void prodProfile_missingMailVariable_fail() {
        MockEnvironment environment = prodEnvironmentExcept("MAIL_USERNAME");
        EnvironmentValidator validator = new EnvironmentValidator(environment);

        assertThatThrownBy(() -> validator.onApplicationEvent(createEvent()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required environment variables")
                .hasMessageContaining("MAIL_USERNAME");
    }

    @Test
    @DisplayName("does not require legacy SES variables that application-prod.yml no longer uses")
    void prodProfile_legacySesVariablesAreNotRequired() {
        MockEnvironment environment = prodEnvironmentExcept();
        EnvironmentValidator validator = new EnvironmentValidator(environment);

        assertThatCode(() -> validator.onApplicationEvent(createEvent()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("reports each missing OAuth secret once")
    void prodProfile_missingOAuthSecrets_reportsEachOnce() {
        MockEnvironment environment = prodEnvironmentExcept("GOOGLE_CLIENT_SECRET", "DISCORD_CLIENT_SECRET");
        EnvironmentValidator validator = new EnvironmentValidator(environment);

        Throwable thrown = catchThrowable(() -> validator.onApplicationEvent(createEvent()));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage())
                .contains("GOOGLE_CLIENT_SECRET")
                .contains("DISCORD_CLIENT_SECRET");
        assertThat(countOccurrences(thrown.getMessage(), "GOOGLE_CLIENT_SECRET"))
                .isEqualTo(1);
        assertThat(countOccurrences(thrown.getMessage(), "DISCORD_CLIENT_SECRET"))
                .isEqualTo(1);
    }

    private MockEnvironment prodEnvironmentExcept(String... missingVariables) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        List<String> missing = List.of(missingVariables);
        for (String variable : REQUIRED_PROD_VARIABLES) {
            if (!missing.contains(variable)) {
                environment.withProperty(variable, "dummy-value");
            }
        }
        return environment;
    }

    private ApplicationReadyEvent createEvent() {
        return new ApplicationReadyEvent(
                mock(SpringApplication.class),
                new String[] {},
                mock(ConfigurableApplicationContext.class),
                Duration.ZERO);
    }

    private int countOccurrences(String value, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(pattern, index)) >= 0) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}
