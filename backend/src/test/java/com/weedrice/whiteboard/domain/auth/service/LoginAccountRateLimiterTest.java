package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.ratelimit.RateLimitConfig;
import com.weedrice.whiteboard.global.ratelimit.RateLimitExceededException;
import com.weedrice.whiteboard.global.ratelimit.RateLimitProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAccountRateLimiterTest {

    @Test
    void recordFailure_blocksAfterConfiguredAccountLimit() {
        LoginAccountRateLimiter limiter = limiterWithAccountLimit(1);

        limiter.assertAllowed("user1");
        limiter.recordFailure("user1");

        assertThatThrownBy(() -> limiter.assertAllowed("user1"))
                .isInstanceOfSatisfying(RateLimitExceededException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
                    assertThat(exception.getSnapshot().limit()).isEqualTo(1);
                    assertThat(exception.getSnapshot().remaining()).isZero();
                    assertThat(exception.getSnapshot().retryAfterSeconds()).isPositive();
                });
    }

    @Test
    void reset_allowsAccountAgainAfterFailureLimit() {
        LoginAccountRateLimiter limiter = limiterWithAccountLimit(1);

        limiter.recordFailure("user1");
        limiter.reset("user1");

        assertThatCode(() -> limiter.assertAllowed("user1")).doesNotThrowAnyException();
    }

    @Test
    void normalizesLoginIdForBucketKey() {
        LoginAccountRateLimiter limiter = limiterWithAccountLimit(1);

        limiter.recordFailure("  User1  ");

        assertThatThrownBy(() -> limiter.assertAllowed("user1"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void separatesBucketsByAccount() {
        LoginAccountRateLimiter limiter = limiterWithAccountLimit(1);

        limiter.recordFailure("user1");

        assertThatCode(() -> limiter.assertAllowed("user2")).doesNotThrowAnyException();
    }

    private LoginAccountRateLimiter limiterWithAccountLimit(int limit) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setAuthAccountLimit(limit);
        return new LoginAccountRateLimiter(new RateLimitConfig(properties), properties);
    }
}
