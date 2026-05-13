package com.weedrice.whiteboard.global.common.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

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
        if (!StringUtils.hasText(remoteAddr)) {
            return false;
        }
        return clientIpProperties.getTrustedProxies().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .anyMatch(remoteAddr.trim()::equals);
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
            String normalizedCandidate = candidate == null ? null : candidate.trim();
            if (StringUtils.hasText(normalizedCandidate)
                    && !"unknown".equalsIgnoreCase(normalizedCandidate)
                    && isIpLiteral(normalizedCandidate)) {
                return normalizedCandidate;
            }
        }
        return null;
    }

    private boolean isIpLiteral(String value) {
        return isIpv4Literal(value) || isIpv6Literal(value);
    }

    private boolean isIpv4Literal(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }

        for (String part : parts) {
            if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) {
                return false;
            }
            int parsedPart;
            try {
                parsedPart = Integer.parseInt(part);
            } catch (NumberFormatException ex) {
                return false;
            }
            if (parsedPart < 0 || parsedPart > 255) {
                return false;
            }
        }
        return true;
    }

    private boolean isIpv6Literal(String value) {
        if (!value.contains(":") || value.contains("%")) {
            return false;
        }
        try {
            return InetAddress.getByName(value) instanceof Inet6Address;
        } catch (UnknownHostException ex) {
            return false;
        }
    }
}
