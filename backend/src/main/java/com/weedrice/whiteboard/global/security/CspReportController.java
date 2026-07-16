package com.weedrice.whiteboard.global.security;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.global.common.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/security")
public class CspReportController {

    private static final int MAX_LOG_VALUE_LENGTH = 180;

    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;

    @PostMapping(value = "/csp-report", consumes = {
            MediaType.APPLICATION_JSON_VALUE,
            "application/csp-report"
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receiveCspReport(
            @RequestBody(required = false) String payload,
            HttpServletRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        JsonNode report = extractReport(payload);
        if (report == null) {
            log.warn(
                    "Malformed CSP violation report ignored. remoteAddress={}, payloadLength={}",
                    clientIp,
                    payload == null ? 0 : payload.length());
            return;
        }
        log.warn(
                "CSP violation report received. remoteAddress={}, documentUri={}, violatedDirective={}, blockedUri={}",
                clientIp,
                sanitizeUri(report.path("document-uri").asString("")),
                sanitizeLogValue(report.path("violated-directive").asString("")),
                sanitizeUri(report.path("blocked-uri").asString("")));
    }

    private JsonNode extractReport(String payload) {
        if (!StringUtils.hasText(payload)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null) {
                return objectMapper.createObjectNode();
            }
            JsonNode nestedReport = root.get("csp-report");
            return nestedReport != null && nestedReport.isObject() ? nestedReport : root;
        } catch (JacksonException ex) {
            return null;
        }
    }

    private String sanitizeUri(String value) {
        int queryIndex = value.indexOf('?');
        int fragmentIndex = value.indexOf('#');
        int endIndex = value.length();
        if (queryIndex >= 0) {
            endIndex = Math.min(endIndex, queryIndex);
        }
        if (fragmentIndex >= 0) {
            endIndex = Math.min(endIndex, fragmentIndex);
        }
        return sanitizeLogValue(value.substring(0, endIndex));
    }

    private String sanitizeLogValue(String value) {
        StringBuilder sanitized = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            sanitized.append(Character.isISOControl(character) ? ' ' : character);
        }
        return truncate(sanitized.toString());
    }

    private String truncate(String value) {
        if (value.length() <= MAX_LOG_VALUE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_LOG_VALUE_LENGTH) + "...";
    }
}
