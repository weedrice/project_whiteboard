package com.weedrice.whiteboard.global.log.filter;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskingFilterTest {

    @Test
    @DisplayName("filter returns neutral for normal messages")
    void decide() {
        SensitiveDataMaskingFilter filter = new SensitiveDataMaskingFilter();
        ILoggingEvent event = Mockito.mock(ILoggingEvent.class);
        Mockito.when(event.getFormattedMessage()).thenReturn("Normal message");

        FilterReply reply = filter.decide(event);

        assertThat(reply).isEqualTo(FilterReply.NEUTRAL);
    }

    @Test
    void maskSensitiveData_masksFieldStyleSecrets() {
        String message = "password=secret pwd=short passwd=legacy token=raw accessToken=access "
                + "refreshToken=refresh apiKey=api accessKey=access-key secretKey=secret-key secret=plain";

        String masked = SensitiveDataMaskingFilter.maskSensitiveData(message);

        assertThat(masked).doesNotContain("password=secret", "pwd=short", "passwd=legacy", "token=raw",
                "accessToken=access", "refreshToken=refresh", "apiKey=api", "accessKey=access-key",
                "secretKey=secret-key", "secret=plain");
        assertThat(masked).contains("password=***MASKED***", "pwd=***MASKED***", "passwd=***MASKED***",
                "token=***MASKED***", "accessToken=***MASKED***", "refreshToken=***MASKED***",
                "apiKey=***MASKED***", "accessKey=***MASKED***", "secretKey=***MASKED***",
                "secret=***MASKED***");
    }

    @Test
    void maskSensitiveData_masksEntireDottedBearerToken() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature";

        String masked = SensitiveDataMaskingFilter.maskSensitiveData("Authorization: Bearer " + jwt);

        assertThat(masked).isEqualTo("Authorization: Bearer ***MASKED***");
        assertThat(masked).doesNotContain("eyJhbGciOiJIUzI1NiJ9", "eyJzdWIiOiIxMjMifQ", "signature");
    }
}
