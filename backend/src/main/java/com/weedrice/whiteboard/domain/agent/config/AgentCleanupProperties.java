package com.weedrice.whiteboard.domain.agent.config;

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
@ConfigurationProperties(prefix = "app.agent")
public class AgentCleanupProperties {

    @Positive
    private int pendingClaimHardDeleteDays = 7;

    @Positive
    private int pendingClaimPurgeBatchSize = 500;

    @Positive
    private int pendingClaimPurgeMaxBatches = 10;
}
