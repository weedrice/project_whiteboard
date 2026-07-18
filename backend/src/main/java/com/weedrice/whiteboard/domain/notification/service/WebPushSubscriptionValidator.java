package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.config.WebPushProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
class WebPushSubscriptionValidator {

    private static final int P256DH_KEY_BYTES = 65;
    private static final int AUTH_SECRET_BYTES = 16;

    private final WebPushProperties properties;
    private final WebPushHostResolver hostResolver;

    void validateForRegistration(String endpoint, String p256dh, String auth) {
        validateEndpoint(endpoint);
        validateKeys(p256dh, auth);
    }

    void validateForDelivery(PushSubscriptionSnapshot subscription) throws UnknownHostException {
        Endpoint endpoint = validateEndpoint(subscription.endpoint());
        validateKeys(subscription.p256dh(), subscription.auth());
        InetAddress[] addresses = hostResolver.resolve(endpoint.host());
        if (addresses.length == 0) {
            throw new UnknownHostException(endpoint.host());
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw invalid("endpoint resolves to a non-public address");
            }
        }
    }

    private Endpoint validateEndpoint(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw invalid("endpoint is blank or contains surrounding whitespace");
        }

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw invalid("endpoint is not a valid URI");
        }
        if (uri.isOpaque() || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw invalid("endpoint must be an HTTPS provider URL without userinfo or fragment");
        }

        String host = normalizeHost(uri.getHost());
        if (isIpLiteral(host)) {
            throw invalid("IP literal endpoints are not allowed");
        }
        if (!isAllowedHost(host, properties.getAllowedHosts())) {
            throw invalid("endpoint provider is not allowed");
        }
        return new Endpoint(host);
    }

    private String normalizeHost(String host) {
        String candidate = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        try {
            return IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw invalid("endpoint host is invalid");
        }
    }

    private boolean isAllowedHost(String host, List<String> configuredHosts) {
        if (configuredHosts == null || configuredHosts.isEmpty()) {
            throw new IllegalStateException("web-push.allowed-hosts must contain at least one provider host");
        }
        for (String configuredHost : configuredHosts) {
            if (configuredHost == null || configuredHost.isBlank()) {
                continue;
            }
            boolean suffix = configuredHost.startsWith(".");
            String allowed = normalizeHost(suffix ? configuredHost.substring(1) : configuredHost);
            if ((!suffix && host.equals(allowed))
                    || (suffix && host.length() > allowed.length() && host.endsWith("." + allowed))) {
                return true;
            }
        }
        return false;
    }

    private boolean isIpLiteral(String host) {
        if (host.indexOf(':') >= 0) {
            return true;
        }
        return host.chars().allMatch(character -> character == '.' || Character.isDigit(character));
    }

    private void validateKeys(String p256dh, String auth) {
        byte[] publicKey = decodeBase64Url(p256dh, "p256dh");
        byte[] authSecret = decodeBase64Url(auth, "auth");
        if (publicKey.length != P256DH_KEY_BYTES || publicKey[0] != 0x04 || !isPointOnP256Curve(publicKey)) {
            throw invalid("p256dh must be an uncompressed P-256 public key");
        }
        if (authSecret.length != AUTH_SECRET_BYTES) {
            throw invalid("auth must be a 16-byte secret");
        }
    }

    private byte[] decodeBase64Url(String value, String field) {
        if (value == null || value.isBlank() || !value.matches("[A-Za-z0-9_-]+={0,2}")) {
            throw invalid(field + " must be Base64URL encoded");
        }
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw invalid(field + " must be Base64URL encoded");
        }
    }

    private boolean isPointOnP256Curve(byte[] encodedPoint) {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
            BigInteger modulus = ((ECFieldFp) spec.getCurve().getField()).getP();
            BigInteger x = new BigInteger(1, java.util.Arrays.copyOfRange(encodedPoint, 1, 33));
            BigInteger y = new BigInteger(1, java.util.Arrays.copyOfRange(encodedPoint, 33, 65));
            if (x.compareTo(modulus) >= 0 || y.compareTo(modulus) >= 0) {
                return false;
            }
            BigInteger left = y.modPow(BigInteger.TWO, modulus);
            BigInteger right = x.modPow(BigInteger.valueOf(3), modulus)
                    .add(spec.getCurve().getA().multiply(x))
                    .add(spec.getCurve().getB())
                    .mod(modulus);
            return left.equals(right);
        } catch (GeneralSecurityException | ClassCastException exception) {
            throw new IllegalStateException("P-256 validation is unavailable", exception);
        }
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            return first != 0
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && second == 0 && third == 0)
                    && !(first == 192 && second == 0 && third == 2)
                    && !(first == 192 && second == 88 && third == 99)
                    && !(first == 198 && (second == 18 || second == 19))
                    && !(first == 198 && second == 51 && third == 100)
                    && !(first == 203 && second == 0 && third == 113)
                    && first < 224;
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            return (first & 0xfe) != 0xfc
                    && !(first == 0x20 && Byte.toUnsignedInt(bytes[1]) == 0x01
                    && Byte.toUnsignedInt(bytes[2]) == 0x0d && Byte.toUnsignedInt(bytes[3]) == 0xb8);
        }
        return false;
    }

    private InvalidWebPushSubscriptionException invalid(String reason) {
        return new InvalidWebPushSubscriptionException(reason);
    }

    private record Endpoint(String host) {
    }
}
