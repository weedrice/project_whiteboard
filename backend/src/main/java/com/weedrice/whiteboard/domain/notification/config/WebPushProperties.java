package com.weedrice.whiteboard.domain.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "web-push")
public class WebPushProperties {

    private String publicKey;
    private String privateKey;
    private String subject;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration responseTimeout = Duration.ofSeconds(10);
    private List<String> allowedHosts = new ArrayList<>(List.of(
            "fcm.googleapis.com",
            "updates.push.services.mozilla.com",
            "web.push.apple.com",
            ".notify.windows.com"));
    private int maxSubscriptionsPerUser = 10;

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public List<String> getAllowedHosts() {
        return List.copyOf(allowedHosts);
    }

    public void setAllowedHosts(List<String> allowedHosts) {
        this.allowedHosts = allowedHosts == null ? new ArrayList<>() : new ArrayList<>(allowedHosts);
    }

    public int getMaxSubscriptionsPerUser() {
        return maxSubscriptionsPerUser;
    }

    public void setMaxSubscriptionsPerUser(int maxSubscriptionsPerUser) {
        this.maxSubscriptionsPerUser = maxSubscriptionsPerUser;
    }

    public boolean isEnabled() {
        return hasText(publicKey) && hasText(privateKey) && hasText(subject);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
