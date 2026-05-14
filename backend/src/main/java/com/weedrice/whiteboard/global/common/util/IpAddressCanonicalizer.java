package com.weedrice.whiteboard.global.common.util;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.StringJoiner;

public final class IpAddressCanonicalizer {

    public static final int MAX_IP_ADDRESS_LENGTH = 45;

    private IpAddressCanonicalizer() {
    }

    public static Optional<String> canonicalize(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String canonicalValue = value.trim();
        if (canonicalValue.length() > MAX_IP_ADDRESS_LENGTH
                || containsWhitespace(canonicalValue)
                || canonicalValue.contains("/")) {
            return Optional.empty();
        }
        return canonicalizeIpv4Literal(canonicalValue)
                .or(() -> canonicalizeIpv6Literal(canonicalValue));
    }

    private static boolean containsWhitespace(String value) {
        return value.chars().anyMatch(Character::isWhitespace);
    }

    private static Optional<String> canonicalizeIpv4Literal(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return Optional.empty();
        }

        StringJoiner joiner = new StringJoiner(".");
        for (String part : parts) {
            if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) {
                return Optional.empty();
            }
            int parsedPart;
            try {
                parsedPart = Integer.parseInt(part);
            } catch (NumberFormatException ex) {
                return Optional.empty();
            }
            if (parsedPart < 0 || parsedPart > 255) {
                return Optional.empty();
            }
            joiner.add(String.valueOf(parsedPart));
        }
        return Optional.of(joiner.toString());
    }

    private static Optional<String> canonicalizeIpv6Literal(String value) {
        if (!value.contains(":") || value.contains("%")) {
            return Optional.empty();
        }
        try {
            InetAddress inetAddress = InetAddress.getByName(value);
            if (!(inetAddress instanceof Inet6Address)) {
                return Optional.empty();
            }
            return Optional.of(formatIpv6Address(inetAddress.getAddress()));
        } catch (UnknownHostException ex) {
            return Optional.empty();
        }
    }

    private static String formatIpv6Address(byte[] addressBytes) {
        int[] groups = new int[8];
        for (int i = 0; i < groups.length; i++) {
            groups[i] = ((addressBytes[i * 2] & 0xff) << 8) | (addressBytes[i * 2 + 1] & 0xff);
        }

        int bestStart = -1;
        int bestLength = 0;
        for (int i = 0; i < groups.length; i++) {
            if (groups[i] != 0) {
                continue;
            }
            int start = i;
            while (i < groups.length && groups[i] == 0) {
                i++;
            }
            int length = i - start;
            if (length > bestLength && length >= 2) {
                bestStart = start;
                bestLength = length;
            }
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < groups.length; i++) {
            if (i == bestStart) {
                builder.append("::");
                i += bestLength - 1;
                continue;
            }
            if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != ':') {
                builder.append(':');
            }
            builder.append(Integer.toHexString(groups[i]));
        }
        return builder.isEmpty() ? "::" : builder.toString();
    }
}
