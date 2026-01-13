package com.weedrice.whiteboard.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class EnvironmentValidatorTest {

    @Mock
    private Environment environment;

    @Test
    @DisplayName("Prod 프로필이 아닐 경우 검증 건너뜀")
    void nonProdProfile_skipsValidation() {
        EnvironmentValidator validator = new EnvironmentValidator(environment);
        
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        assertThatCode(() -> validator.onApplicationEvent(createEvent()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Prod 프로필일 때 필수 환경 변수가 모두 존재하면 통과")
    void prodProfile_validVariables_success() {
        EnvironmentValidator validator = new EnvironmentValidator(environment);

        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        
        // Mock all required variables to return a dummy value
        when(environment.getProperty(anyString())).thenReturn("dummy-value");

        assertThatCode(() -> validator.onApplicationEvent(createEvent()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Prod 프로필일 때 필수 환경 변수 누락되면 예외 발생")
    void prodProfile_missingVariable_fail() {
        EnvironmentValidator validator = new EnvironmentValidator(environment);

        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        
        // Mock DB_HOST to be missing
        when(environment.getProperty("DB_HOST")).thenReturn(null);

        assertThatThrownBy(() -> validator.onApplicationEvent(createEvent()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required environment variables");
    }

    private ApplicationReadyEvent createEvent() {
        return new ApplicationReadyEvent(mock(SpringApplication.class), new String[]{}, mock(ConfigurableApplicationContext.class), Duration.ZERO);
    }
}
