package com.weedrice.whiteboard.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 환경 변수 검증 클래스
 * 
 * 프로덕션 환경에서 필수 환경 변수가 설정되었는지 확인합니다.
 * 필수 환경 변수가 누락된 경우 애플리케이션 시작을 중단합니다.
 */
@Slf4j
@Component
public class EnvironmentValidator implements ApplicationListener<ApplicationReadyEvent> {

    private final Environment environment;

    public EnvironmentValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        String[] activeProfiles = environment.getActiveProfiles();
        
        // 프로덕션 환경에서만 검증
        boolean isProduction = false;
        for (String profile : activeProfiles) {
            if ("prod".equals(profile)) {
                isProduction = true;
                break;
            }
        }

        if (!isProduction) {
            log.info("Non-production environment detected. Skipping environment variable validation.");
            return;
        }

        log.info("Validating required environment variables for production environment...");
        
        List<String> missingVariables = new ArrayList<>();
        
        // 필수 환경 변수 목록
        String[] requiredVariables = {
            // Database
            "DB_HOST",
            "DB_NAME",
            "DB_USER",
            "DB_PASSWORD",
            
            // JWT
            "JWT_SECRET",
            
            // OAuth - GitHub
            "GITHUB_CLIENT_ID",
            "GITHUB_CLIENT_SECRET",
            
            // OAuth - Google (선택적이지만 설정되어 있으면 검증)
            "GOOGLE_CLIENT_ID",
            // "GOOGLE_CLIENT_SECRET",
            
            // OAuth - Discord
            "DISCORD_CLIENT_ID",
            "DISCORD_CLIENT_SECRET",
            
            // AWS
            "AWS_ACCESS_KEY",
            "AWS_SECRET_KEY",
            "AWS_SES_ACCESS_KEY",
            "AWS_SES_SECRET_KEY",
            "AWS_SES_SENDER",
            "AWS_S3_REGION",
            "S3_BUCKET",
            
            // Frontend
            "FRONTEND_URL"
        };

        // 필수 환경 변수 검증
        for (String variable : requiredVariables) {
            String value = environment.getProperty(variable);
            if (value == null || value.trim().isEmpty()) {
                missingVariables.add(variable);
            }
        }

        // OAuth 설정 검증 (하나라도 설정되어 있으면 모두 필수)
        String googleClientId = environment.getProperty("GOOGLE_CLIENT_ID");
        String discordClientId = environment.getProperty("DISCORD_CLIENT_ID");
        
        if (googleClientId != null && !googleClientId.trim().isEmpty()) {
            String googleClientSecret = environment.getProperty("GOOGLE_CLIENT_SECRET");
            if (googleClientSecret == null || googleClientSecret.trim().isEmpty()) {
                missingVariables.add("GOOGLE_CLIENT_SECRET");
            }
        }
        
        if (discordClientId != null && !discordClientId.trim().isEmpty()) {
            String discordClientSecret = environment.getProperty("DISCORD_CLIENT_SECRET");
            if (discordClientSecret == null || discordClientSecret.trim().isEmpty()) {
                missingVariables.add("DISCORD_CLIENT_SECRET");
            }
        }

        if (!missingVariables.isEmpty()) {
            @SuppressWarnings("null")
            String errorMessage = String.format(
                "Missing required environment variables for production environment: %s",
                String.join(", ", missingVariables)
            );
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
}
