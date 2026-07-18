package com.weedrice.whiteboard.domain.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.oauth.signup-ticket")
public class OAuthSignupTicketProperties {

    @NotNull
    private Duration ttl = Duration.ofMinutes(10);

    @AssertTrue(message = "app.oauth.signup-ticket.ttl must be positive")
    public boolean isTtlPositive() {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }
}
