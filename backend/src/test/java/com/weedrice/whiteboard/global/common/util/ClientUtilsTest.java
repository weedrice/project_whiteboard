package com.weedrice.whiteboard.global.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientUtilsTest {

    @Test
    @DisplayName("IP 추출 - X-Forwarded-For")
    void getIp_xForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        String ip = ClientUtils.getIp(request);
        assertThat(ip).isEqualTo("1.2.3.4");
    }

    @Test
    @DisplayName("IP 추출 - RemoteAddr")
    void getIp_remoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        String ip = ClientUtils.getIp(request);
        assertThat(ip).isEqualTo("127.0.0.1");
    }
    
    @Test
    @DisplayName("ClientUtils 생성자 테스트")
    void constructor() {
        new ClientUtils(); // 커버리지용
    }
}
