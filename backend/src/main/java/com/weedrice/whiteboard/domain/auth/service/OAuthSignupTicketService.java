package com.weedrice.whiteboard.domain.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.weedrice.whiteboard.domain.auth.dto.OAuthSignupTicketResponse;
import com.weedrice.whiteboard.global.common.util.TextInputNormalizer;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class OAuthSignupTicketService {

    private static final int TICKET_LENGTH = 36;

    private final Cache<String, OAuthSignupTicket> tickets = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(10_000)
            .build();

    public String issue(String email, String name, String provider, String providerId) {
        String ticket = UUID.randomUUID().toString();
        tickets.put(ticket, new OAuthSignupTicket(email, name, provider, providerId));
        return ticket;
    }

    public OAuthSignupTicketResponse getResponse(String ticket) {
        OAuthSignupTicket payload = get(ticket);
        return OAuthSignupTicketResponse.builder()
                .email(payload.email())
                .name(payload.name())
                .provider(payload.provider())
                .build();
    }

    public OAuthSignupTicket consume(String ticket) {
        OAuthSignupTicket payload = get(ticket);
        tickets.invalidate(ticket);
        return payload;
    }

    private OAuthSignupTicket get(String ticket) {
        String normalizedTicket = normalizeTicket(ticket);
        OAuthSignupTicket payload = tickets.getIfPresent(normalizedTicket);
        if (payload == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return payload;
    }

    private String normalizeTicket(String ticket) {
        String normalizedTicket = TextInputNormalizer.normalizeRequired(ticket, TICKET_LENGTH);
        try {
            UUID.fromString(normalizedTicket);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedTicket;
    }

    public record OAuthSignupTicket(String email, String name, String provider, String providerId) {
    }
}
