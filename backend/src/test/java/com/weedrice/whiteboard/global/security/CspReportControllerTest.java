package com.weedrice.whiteboard.global.security;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CspReportControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new CspReportController(new ObjectMapper()))
            .build();

    @Test
    void receiveCspReportAcceptsStandardContentTypeWithCharset() throws Exception {
        String payload = """
                {
                  "csp-report": {
                    "document-uri": "https://noviis.kr/",
                    "violated-directive": "script-src-elem",
                    "blocked-uri": "inline"
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/security/csp-report")
                        .contentType(MediaType.parseMediaType("application/csp-report;charset=UTF-8"))
                        .content(payload))
                .andExpect(status().isNoContent());
    }

    @Test
    void receiveCspReportAcceptsApplicationJson() throws Exception {
        mockMvc.perform(post("/api/v1/security/csp-report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"document-uri\":\"https://noviis.kr/\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void receiveCspReportIgnoresMalformedPayload() throws Exception {
        mockMvc.perform(post("/api/v1/security/csp-report")
                        .contentType("application/csp-report")
                        .content("{not-json"))
                .andExpect(status().isNoContent());
    }

    @Test
    void receiveCspReportAcceptsEmptyPayload() throws Exception {
        mockMvc.perform(post("/api/v1/security/csp-report")
                        .contentType("application/csp-report"))
                .andExpect(status().isNoContent());
    }

    @Test
    void receiveCspReportIgnoresOversizedPayload() throws Exception {
        String payload = "{\"value\":\"" + "a".repeat(32 * 1024) + "\"}";

        mockMvc.perform(post("/api/v1/security/csp-report")
                        .contentType("application/csp-report")
                        .content(payload))
                .andExpect(status().isNoContent());
    }
}
