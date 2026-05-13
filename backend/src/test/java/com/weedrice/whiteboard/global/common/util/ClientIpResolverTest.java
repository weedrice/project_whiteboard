package com.weedrice.whiteboard.global.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.env.MockEnvironment;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    @DisplayName("client-ip prefix 설정을 ClientIpProperties에 바인딩한다")
    void bindClientIpProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("client-ip.trust-proxy-headers", "true")
                .withProperty("client-ip.trusted-proxies[0]", "10.0.0.10");

        ClientIpProperties properties = Binder.get(environment)
                .bind("client-ip", Bindable.of(ClientIpProperties.class))
                .get();

        assertThat(properties.isTrustProxyHeaders()).isTrue();
        assertThat(properties.getTrustedProxies()).containsExactly("10.0.0.10");
    }

    @Test
    @DisplayName("프록시 헤더 신뢰가 꺼져 있으면 remoteAddr을 사용한다")
    void resolve_usesRemoteAddrWhenProxyTrustDisabled() {
        ClientIpResolver resolver = new ClientIpResolver(new ClientIpProperties());
        MockHttpServletRequest request = request("10.0.0.10");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        String clientIp = resolver.resolve(request);

        assertThat(clientIp).isEqualTo("10.0.0.10");
    }

    @Test
    @DisplayName("신뢰 프록시 목록이 비어 있으면 forwarded header를 무시한다")
    void resolve_ignoresForwardedHeaderWhenTrustedProxyListIsEmpty() {
        ClientIpProperties properties = new ClientIpProperties();
        properties.setTrustProxyHeaders(true);
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = request("10.0.0.10");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        String clientIp = resolver.resolve(request);

        assertThat(clientIp).isEqualTo("10.0.0.10");
    }

    @Test
    @DisplayName("신뢰하지 않는 remoteAddr의 forwarded header를 무시한다")
    void resolve_ignoresForwardedHeaderFromUntrustedRemoteAddr() {
        ClientIpProperties properties = trustedProxyProperties("10.0.0.10");
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = request("10.0.0.20");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        String clientIp = resolver.resolve(request);

        assertThat(clientIp).isEqualTo("10.0.0.20");
    }

    @Test
    @DisplayName("신뢰 프록시에서 온 X-Forwarded-For의 첫 유효 IP를 사용한다")
    void resolve_usesFirstForwardedIpFromTrustedProxy() {
        ClientIpProperties properties = trustedProxyProperties("10.0.0.10");
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = request("10.0.0.10");
        request.addHeader("X-Forwarded-For", " unknown, not-an-ip, 203.0.113.10, 10.0.0.1 ");

        String clientIp = resolver.resolve(request);

        assertThat(clientIp).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("신뢰 프록시에서 온 IPv6 forwarded header를 사용할 수 있다")
    void resolve_usesForwardedIpv6FromTrustedProxy() {
        ClientIpProperties properties = trustedProxyProperties("2001:db8::10");
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = request("2001:db8::10");
        request.addHeader("X-Forwarded-For", "2001:db8::20");

        String clientIp = resolver.resolve(request);

        assertThat(clientIp).isEqualTo("2001:db8::20");
    }

    @Test
    @DisplayName("신뢰 프록시 요청에서 X-Forwarded-For가 없으면 X-Real-IP를 사용한다")
    void resolve_usesXRealIpWhenForwardedForMissing() {
        ClientIpProperties properties = trustedProxyProperties("10.0.0.10");
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = request("10.0.0.10");
        request.addHeader("X-Real-IP", "203.0.113.11");

        String clientIp = resolver.resolve(request);

        assertThat(clientIp).isEqualTo("203.0.113.11");
    }

    @Test
    @DisplayName("신뢰 프록시 요청에 유효한 헤더가 없으면 remoteAddr로 대체한다")
    void resolve_fallsBackToRemoteAddrWhenForwardedHeadersAreInvalid() {
        ClientIpProperties properties = trustedProxyProperties("10.0.0.10");
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = request("10.0.0.10");
        request.addHeader("X-Forwarded-For", "unknown,   ");

        String clientIp = resolver.resolve(request);

        assertThat(clientIp).isEqualTo("10.0.0.10");
    }

    private ClientIpProperties trustedProxyProperties(String trustedProxy) {
        ClientIpProperties properties = new ClientIpProperties();
        properties.setTrustProxyHeaders(true);
        properties.setTrustedProxies(Set.of(trustedProxy));
        return properties;
    }

    private MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
