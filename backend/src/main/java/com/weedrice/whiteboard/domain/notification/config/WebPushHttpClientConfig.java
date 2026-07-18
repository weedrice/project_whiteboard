package com.weedrice.whiteboard.domain.notification.config;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class WebPushHttpClientConfig {

    @Bean(destroyMethod = "close")
    public CloseableHttpAsyncClient webPushHttpClient(WebPushProperties properties) {
        int connectTimeoutMillis = positiveMillis(properties.getConnectTimeout(), "web-push.connect-timeout");
        int responseTimeoutMillis = positiveMillis(properties.getResponseTimeout(), "web-push.response-timeout");
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeoutMillis)
                .setConnectionRequestTimeout(connectTimeoutMillis)
                .setSocketTimeout(responseTimeoutMillis)
                .setRedirectsEnabled(false)
                .build();
        CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .useSystemProperties()
                .setDefaultRequestConfig(requestConfig)
                .build();
        client.start();
        return client;
    }

    private int positiveMillis(Duration duration, String propertyName) {
        if (duration == null || duration.isZero() || duration.isNegative()
                || duration.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalStateException(propertyName + " must be between 1ms and " + Integer.MAX_VALUE + "ms");
        }
        return Math.toIntExact(duration.toMillis());
    }
}
