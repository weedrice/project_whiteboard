package com.weedrice.whiteboard.global.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/security")
public class CspReportController {

    private static final int MAX_LOG_VALUE_LENGTH = 180;

    @PostMapping(value = "/csp-report", consumes = {
            MediaType.APPLICATION_JSON_VALUE,
            "application/csp-report"
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receiveCspReport(
            @RequestBody(required = false) Map<String, Object> payload,
            HttpServletRequest request) {
        Map<String, Object> report = extractReport(payload);
        log.warn(
                "CSP violation report received. remoteAddress={}, documentUri={}, violatedDirective={}, blockedUri={}",
                request.getRemoteAddr(),
                truncate(stringValue(report.get("document-uri"))),
                truncate(stringValue(report.get("violated-directive"))),
                truncate(stringValue(report.get("blocked-uri"))));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractReport(Map<String, Object> payload) {
        if (payload == null) {
            return Map.of();
        }
        Object nestedReport = payload.get("csp-report");
        if (nestedReport instanceof Map<?, ?>) {
            return (Map<String, Object>) nestedReport;
        }
        return payload;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String truncate(String value) {
        if (value.length() <= MAX_LOG_VALUE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_LOG_VALUE_LENGTH) + "...";
    }
}
