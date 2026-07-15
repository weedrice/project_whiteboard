package com.weedrice.whiteboard.global.common.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NginxForwardedHeaderConfigTest {

    private static final List<Path> NGINX_CONFIG_PATHS = List.of(
            Path.of("..", "deploy", "nginx", "noviis.conf"),
            Path.of("..", "frontend", "nginx.conf"));

    @Test
    void everyProxyLocationSanitizesForwardedHeadersAtThePublicEdge() throws IOException {
        for (Path configPath : NGINX_CONFIG_PATHS) {
            String config = Files.readString(configPath, StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            List<String> proxyLocations = Arrays.stream(config.split("(?m)^    location "))
                    .filter(block -> block.contains("proxy_pass "))
                    .toList();

            assertThat(proxyLocations)
                    .as("proxy locations in %s", configPath)
                    .isNotEmpty()
                    .allSatisfy(block -> assertThat(block)
                            .contains(
                                    "proxy_set_header X-Real-IP $remote_addr;",
                                    "proxy_set_header X-Forwarded-For $remote_addr;",
                                    "proxy_set_header X-Forwarded-Proto $scheme;",
                                    "proxy_set_header Forwarded \"\";",
                                    "proxy_set_header X-Forwarded-Host \"\";",
                                    "proxy_set_header X-Forwarded-Port \"\";",
                                    "proxy_set_header X-Forwarded-Prefix \"\";",
                                    "proxy_set_header X-Forwarded-Ssl \"\";")
                            .doesNotContain("$proxy_add_x_forwarded_for"));
        }
    }

    @Test
    void productionEdgeRejectsUnknownHostHeaders() throws IOException {
        String config = Files.readString(NGINX_CONFIG_PATHS.getFirst(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");

        assertThat(config).contains(
                "if ($host !~* ^(noviis\\.kr|www\\.noviis\\.kr)$) {\n        return 444;\n    }");
    }
}
