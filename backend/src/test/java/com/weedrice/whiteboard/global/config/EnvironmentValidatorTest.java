package com.weedrice.whiteboard.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;

class EnvironmentValidatorTest {

    private static final List<String> REQUIRED_PROD_VARIABLES = EnvironmentValidator.REQUIRED_PROD_VARIABLES;

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

    @Test
    @DisplayName("production environment documentation matches the validator contract")
    void productionDocumentation_matchesValidatorContract() throws IOException {
        Path document = Path.of("ENVIRONMENT_VARIABLES.md");
        String markdown = Files.readString(document, StandardCharsets.UTF_8);
        String requiredSection = markdown.substring(
                markdown.indexOf("## Required In Production"),
                markdown.indexOf("## Optional Or Deployment-Specific"));
        Matcher matcher = Pattern.compile("\\| `([A-Z0-9_]+)` ").matcher(requiredSection);
        Set<String> documented = matcher.results()
                .map(result -> result.group(1))
                .collect(Collectors.toSet());

        assertThat(documented).containsExactlyInAnyOrderElementsOf(REQUIRED_PROD_VARIABLES);
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
