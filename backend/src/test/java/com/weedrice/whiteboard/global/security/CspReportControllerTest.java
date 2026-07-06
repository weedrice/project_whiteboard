package com.weedrice.whiteboard.global.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;

class CspReportControllerTest {

    private final CspReportController controller = new CspReportController();

    @Test
    void receiveCspReportAcceptsStandardNestedPayload() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        Map<String, Object> payload = Map.of(
                "csp-report", Map.of(
                        "document-uri", "https://noviis.kr/",
                        "violated-directive", "script-src-elem",
                        "blocked-uri", "inline"));

        assertThatNoException().isThrownBy(() -> controller.receiveCspReport(payload, request));
    }

    @Test
    void receiveCspReportAcceptsEmptyPayload() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatNoException().isThrownBy(() -> controller.receiveCspReport(null, request));
    }
}
