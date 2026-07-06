package com.weedrice.whiteboard.global.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds rate-limit configuration from application.yml.
 *
 * <pre>
 * rate-limit:
 *   enabled: true
 *   default-limit: 100
 *   auth-limit: 5
 *   auth-account-limit: 5
 *   api-limit: 200
 *   user-limit: 500
 *   bucket-cache-max-size: 20000
 *   bucket-cache-ttl-minutes: 120
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int defaultLimit = 100;
    private int authLimit = 5;
    private int authAccountLimit = 5;
    private int apiLimit = 200;
    private int userLimit = 500;
    private long bucketCacheMaxSize = 20_000;
    private int bucketCacheTtlMinutes = 120;
}
