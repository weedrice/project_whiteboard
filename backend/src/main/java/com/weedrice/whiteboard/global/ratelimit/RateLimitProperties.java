package com.weedrice.whiteboard.global.ratelimit;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds rate-limit configuration from application.yml.
 *
 * <pre>
 * rate-limit:
 *   enabled: true
 *   auth-limit: 5
 *   agent-register-limit: 5
 *   public-log-limit: 10
 *   auth-account-limit: 5
 *   api-limit: 200
 *   user-limit: 500
 *   bucket-cache-max-size: 20000
 *   bucket-cache-ttl-minutes: 120
 * </pre>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    @Positive
    private int authLimit = 5;
    @Positive
    private int agentRegisterLimit = 5;
    @Positive
    private int publicLogLimit = 10;
    @Positive
    private int authAccountLimit = 5;
    @Positive
    private int apiLimit = 200;
    @Positive
    private int userLimit = 500;
    @Positive
    private long bucketCacheMaxSize = 20_000;
    @Positive
    private int bucketCacheTtlMinutes = 120;
}
