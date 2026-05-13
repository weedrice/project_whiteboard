package com.weedrice.whiteboard.global.common.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@ConfigurationProperties(prefix = "client-ip")
public class ClientIpProperties {

    private boolean trustProxyHeaders = false;
    private Set<String> trustedProxies = new LinkedHashSet<>();
}
