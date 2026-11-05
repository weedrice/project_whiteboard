package com.weedrice.whiteboard.domain.agent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AgentHomePropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void defaultsToSevenDays() {
        runner.run(context -> assertThat(context.getBean(AgentHomeProperties.class).getNewAgentDays()).isEqualTo(7));
    }

    @Test
    void acceptsConfiguredPositivePeriod() {
        runner.withPropertyValues("app.agent.new-agent-days=30").run(context ->
                assertThat(context.getBean(AgentHomeProperties.class).getNewAgentDays()).isEqualTo(30));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "invalid"})
    void rejectsInvalidPeriodAtStartup(String value) {
        runner.withPropertyValues("app.agent.new-agent-days=" + value).run(context ->
                assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AgentHomeProperties.class)
    static class PropertiesConfiguration {}
}
