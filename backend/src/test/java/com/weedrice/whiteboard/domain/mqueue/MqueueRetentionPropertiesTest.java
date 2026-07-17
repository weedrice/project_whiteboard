package com.weedrice.whiteboard.domain.mqueue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MqueueRetentionPropertiesTest {

    @Test
    void applicationConfigurationExposesUnconfirmedInvestigationGrace() throws IOException {
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader().load(
                "application",
                new FileSystemResource("src/main/resources/application.yml"));

        assertThat(propertySources)
                .extracting(source -> source.getProperty(
                        "app.message-queue.delivered-unconfirmed-retention-days"))
                .contains("${APP_MESSAGE_QUEUE_DELIVERED_UNCONFIRMED_RETENTION_DAYS:7}");
    }
}
