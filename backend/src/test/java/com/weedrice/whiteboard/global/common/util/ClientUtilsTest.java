package com.weedrice.whiteboard.global.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientUtilsTest {

    @Test
    @DisplayName("X-Forwarded-For 헤더에서 IP 추출")
    void getIp_xForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.1, 192.168.0.1");
        
        String ip = ClientUtils.getIp(request);
        assertThat(ip).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("Proxy-Client-IP 헤더 확인")
    void getIp_proxyClientIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Proxy-Client-IP", "10.0.0.2");
        
        String ip = ClientUtils.getIp(request);
        assertThat(ip).isEqualTo("10.0.0.2");
    }

    @Test
    @DisplayName("WL-Proxy-Client-IP 헤더 확인")
    void getIp_wlProxyClientIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("WL-Proxy-Client-IP", "10.0.0.3");
        
        String ip = ClientUtils.getIp(request);
        assertThat(ip).isEqualTo("10.0.0.3");
    }

    @Test
    @DisplayName("HTTP_CLIENT_IP 헤더 확인")
    void getIp_httpClientIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("HTTP_CLIENT_IP", "10.0.0.4");
        
        String ip = ClientUtils.getIp(request);
        assertThat(ip).isEqualTo("10.0.0.4");
    }

    @Test
    @DisplayName("HTTP_X_FORWARDED_FOR 헤더 확인")
    void getIp_httpXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("HTTP_X_FORWARDED_FOR", "10.0.0.5");
        
        String ip = ClientUtils.getIp(request);
        assertThat(ip).isEqualTo("10.0.0.5");
    }

    @Test
    @DisplayName("헤더가 없으면 RemoteAddr 반환")
    void getIp_remoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        
        String ip = ClientUtils.getIp(request);
        assertThat(ip).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("unknown 값 무시 확인")
    void getIp_ignoreUnknown() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "unknown");
        request.addHeader("Proxy-Client-IP", "10.0.0.6");
        
        String ip = ClientUtils.getIp(request);
        assertThat(ip).isEqualTo("10.0.0.6");
    }
}