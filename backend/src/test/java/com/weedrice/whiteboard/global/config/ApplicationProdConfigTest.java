package com.weedrice.whiteboard.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationProdConfigTest {

    @Test
    @DisplayName("production OAuth callbacks use Spring Security callback template")
    void oauthRedirectUris_useSpringSecurityCallbackTemplate() throws IOException {
        PropertySource<?> prodConfig = loadProdConfig();

        assertThat(prodConfig.getProperty("spring.security.oauth2.client.registration.github.redirect-uri"))
                .isEqualTo("{baseUrl}/login/oauth2/code/{registrationId}");
        assertThat(prodConfig.getProperty("spring.security.oauth2.client.registration.google.redirect-uri"))
                .isEqualTo("{baseUrl}/login/oauth2/code/{registrationId}");
        assertThat(prodConfig.getProperty("spring.security.oauth2.client.registration.discord.redirect-uri"))
                .isEqualTo("{baseUrl}/login/oauth2/code/{registrationId}");
    }

    @Test
    @DisplayName("production config maps required agent internal secret")
    void agentInternalSecret_mapsRequiredEnvironmentVariable() throws IOException {
        PropertySource<?> prodConfig = loadProdConfig();

        assertThat(prodConfig.getProperty("app.agent.internal-secret"))
                .isEqualTo("${AGENT_INTERNAL_SECRET}");
    }

    @Test
    @DisplayName("production uses forwarded headers for OAuth base URL resolution")
    void forwardedHeaders_enabledForPublicProxyBaseUrl() throws IOException {
        PropertySource<?> prodConfig = loadProdConfig();

        assertThat(prodConfig.getProperty("server.forward-headers-strategy"))
                .isEqualTo("framework");
    }

    @Test
    @DisplayName("production loads the production logback profile")
    void loggingConfig_usesProductionLogback() throws IOException {
        PropertySource<?> prodConfig = loadProdConfig();

        assertThat(prodConfig.getProperty("logging.config"))
                .isEqualTo("classpath:logback-prod.xml");
    }

    @Test
    @DisplayName("production disables OpenAPI JSON by default")
    void openApiJson_disabledInProduction() throws IOException {
        PropertySource<?> prodConfig = loadProdConfig();

        assertThat(prodConfig.getProperty("springdoc.api-docs.enabled"))
                .isEqualTo(false);
    }

    private PropertySource<?> loadProdConfig() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> propertySources = loader.load(
                "application-prod",
                new ClassPathResource("application-prod.yml"));
        return propertySources.get(0);
    }
}
