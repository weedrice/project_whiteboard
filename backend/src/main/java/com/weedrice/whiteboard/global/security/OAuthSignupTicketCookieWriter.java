package com.weedrice.whiteboard.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
public class OAuthSignupTicketCookieWriter {

    public static final String COOKIE_NAME = "oauthSignupTicket";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final Duration ticketTtl;
    private final boolean productionProfile;

    public OAuthSignupTicketCookieWriter(
            @Value("${app.oauth.signup-ticket-ttl:10m}") Duration ticketTtl,
            Environment environment) {
        this.ticketTtl = ticketTtl;
        this.productionProfile = environment != null && environment.acceptsProfiles(Profiles.of("prod"));
    }

    public void write(HttpServletResponse response, String ticket, HttpServletRequest request) {
        if (!StringUtils.hasText(ticket)) {
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(ticket, ticketTtl, request).toString());
    }

    public void clear(HttpServletResponse response, HttpServletRequest request) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO, request).toString());
    }

    private ResponseCookie cookie(String value, Duration maxAge, HttpServletRequest request) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (productionProfile || request == null || request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (!StringUtils.hasText(forwardedProto)) {
            return false;
        }
        return "https".equalsIgnoreCase(forwardedProto.split(",", 2)[0].trim());
    }
}
