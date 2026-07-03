package com.weedrice.whiteboard.global.common.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
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
                .anyMatch(trustedProxy -> matchesTrustedProxy(normalizedRemoteAddr, trustedProxy));
    }

    private boolean matchesTrustedProxy(String normalizedIp, String trustedProxy) {
        if (!StringUtils.hasText(trustedProxy)) {
            return false;
        }
        if (trustedProxy.contains("/")) {
            return matchesCidr(normalizedIp, trustedProxy.trim());
        }
        return IpAddressCanonicalizer.canonicalize(trustedProxy)
                .map(normalizedIp::equals)
                .orElse(false);
    }

    private boolean matchesCidr(String normalizedIp, String cidr) {
        String[] parts = cidr.split("/", -1);
        if (parts.length != 2) {
            return false;
        }

        Optional<String> normalizedNetwork = IpAddressCanonicalizer.canonicalize(parts[0]);
        if (normalizedNetwork.isEmpty()) {
            return false;
        }

        byte[] ipBytes = toAddressBytes(normalizedIp).orElse(null);
        byte[] networkBytes = toAddressBytes(normalizedNetwork.get()).orElse(null);
        if (ipBytes == null || networkBytes == null || ipBytes.length != networkBytes.length) {
            return false;
        }

        int prefixLength;
        try {
            prefixLength = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return false;
        }
        if (prefixLength < 0 || prefixLength > ipBytes.length * 8) {
            return false;
        }

        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;
        if (!Arrays.equals(Arrays.copyOf(ipBytes, fullBytes), Arrays.copyOf(networkBytes, fullBytes))) {
            return false;
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xff << (8 - remainingBits);
        return (ipBytes[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
    }

    private Optional<byte[]> toAddressBytes(String normalizedIp) {
        try {
            return Optional.of(InetAddress.getByName(normalizedIp).getAddress());
        } catch (UnknownHostException ex) {
            return Optional.empty();
        }
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

        List<String> candidates = Arrays.stream(headerValue.split(","))
                .map(candidate -> {
                    if (candidate != null && "unknown".equalsIgnoreCase(candidate.trim())) {
                        return null;
                    }
                    return IpAddressCanonicalizer.canonicalize(candidate).orElse(null);
                })
                .filter(StringUtils::hasText)
                .toList();

        for (int i = candidates.size() - 1; i >= 0; i--) {
            String candidate = candidates.get(i);
            if (!isTrustedProxy(candidate)) {
                return candidate;
            }
        }

        if (!candidates.isEmpty()) {
            return candidates.get(0);
        }
        return null;
    }
}
