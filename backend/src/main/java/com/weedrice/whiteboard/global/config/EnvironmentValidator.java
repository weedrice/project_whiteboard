package com.weedrice.whiteboard.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Validates that required production environment variables are present.
 */
@Slf4j
@Component
public class EnvironmentValidator implements ApplicationListener<ApplicationReadyEvent> {

    private static final String PROD_PROFILE = "prod";

    private static final List<String> REQUIRED_PROD_VARIABLES = List.of(
            // Database
            "DB_HOST",
            "DB_NAME",
            "DB_USER",
            "DB_PASSWORD",

            // JWT
            "JWT_SECRET",

            // OAuth providers configured in application-prod.yml
            "GITHUB_CLIENT_ID",
            "GITHUB_CLIENT_SECRET",
            "GOOGLE_CLIENT_ID",
            "GOOGLE_CLIENT_SECRET",
            "DISCORD_CLIENT_ID",
            "DISCORD_CLIENT_SECRET",

            // SMTP mail
            "MAIL_USERNAME",
            "MAIL_APP_PASSWORD",

            // S3
            "AWS_ACCESS_KEY",
            "AWS_SECRET_KEY",
            "AWS_S3_REGION",
            "S3_BUCKET",

            // Frontend
            "FRONTEND_URL");

    private final Environment environment;

    public EnvironmentValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        if (!isProductionProfile()) {
            log.info("Non-production environment detected. Skipping environment variable validation.");
            return;
        }

        log.info("Validating required environment variables for production environment...");

        List<String> missingVariables = findMissingRequiredVariables();

        if (!missingVariables.isEmpty()) {
            String errorMessage = String.format(
                    "Missing required environment variables for production environment: %s",
                    String.join(", ", missingVariables));
            log.error("=".repeat(80));
            log.error("ENVIRONMENT VALIDATION FAILED");
            log.error("=".repeat(80));
            log.error(errorMessage);
            log.error("");
            log.error("Please set the following environment variables:");
            missingVariables.forEach(var -> log.error("  - {}", var));
            log.error("");
            log.error("Refer to ENVIRONMENT_VARIABLES.md for detailed information.");
            log.error("=".repeat(80));

            throw new IllegalStateException(errorMessage);
        }

        log.info("Environment variable validation passed. All required variables are set.");
    }

    private boolean isProductionProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains(PROD_PROFILE);
    }

    private List<String> findMissingRequiredVariables() {
        List<String> missingVariables = new ArrayList<>();
        for (String variable : REQUIRED_PROD_VARIABLES) {
            String value = environment.getProperty(variable);
            if (value == null || value.trim().isEmpty()) {
                missingVariables.add(variable);
            }
        }
        return missingVariables;
    }
}
