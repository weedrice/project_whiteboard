package com.weedrice.whiteboard.global.common.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private static final List<String> FORWARDED_HEADER_NAMES = List.of(
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR");

    private final ClientIpProperties clientIpProperties;

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!clientIpProperties.isTrustProxyHeaders() || !isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        return resolveForwardedIp(request, remoteAddr);
    }

    private boolean isTrustedProxy(String remoteAddr) {
        String normalizedRemoteAddr = IpAddressCanonicalizer.canonicalize(remoteAddr).orElse(null);
        if (!StringUtils.hasText(normalizedRemoteAddr)) {
            return false;
        }
        return clientIpProperties.getTrustedProxies().stream()
                .map(IpAddressCanonicalizer::canonicalize)
                .flatMap(Optional::stream)
                .anyMatch(normalizedRemoteAddr::equals);
    }

    private String resolveForwardedIp(HttpServletRequest request, String fallbackIp) {
        for (String headerName : FORWARDED_HEADER_NAMES) {
            String candidate = selectForwardedIp(request.getHeader(headerName));
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return fallbackIp;
    }

    private String selectForwardedIp(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }

        String[] candidates = headerValue.split(",");
        for (String candidate : candidates) {
            if (candidate != null && "unknown".equalsIgnoreCase(candidate.trim())) {
                continue;
            }
            Optional<String> normalizedCandidate = IpAddressCanonicalizer.canonicalize(candidate);
            if (normalizedCandidate.isPresent()) {
                return normalizedCandidate.get();
            }
        }
        return null;
    }
}
