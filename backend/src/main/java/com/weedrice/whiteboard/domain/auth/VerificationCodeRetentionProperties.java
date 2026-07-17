package com.weedrice.whiteboard.domain.auth;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.verification-code")
public class VerificationCodeRetentionProperties {

    @Positive
    private int terminalRetentionDays = 7;

    @Positive
    private int pendingRecoveryGraceMinutes = 30;

    @Positive
    private int pendingRecoveryBatchSize = 500;

    @Positive
    private int pendingRecoveryMaxBatches = 10;

    @Positive
    private int cleanupBatchSize = 500;

    @Positive
    private int passwordResetTokenRetentionDays = 30;

    @Positive
    private int passwordResetTokenCleanupBatchSize = 500;
}
