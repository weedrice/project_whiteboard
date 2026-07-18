package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.OAuthSignupTicketResponse;
import com.weedrice.whiteboard.domain.auth.OAuthSignupTicketProperties;
import com.weedrice.whiteboard.domain.auth.entity.OAuthSignupTicketEntity;
import com.weedrice.whiteboard.domain.auth.repository.OAuthSignupTicketRepository;
import com.weedrice.whiteboard.global.common.util.TextInputNormalizer;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OAuthSignupTicketService {

    private static final int TICKET_LENGTH = 36;
    public static final int CLEANUP_BATCH_SIZE = 500;
    private final OAuthSignupTicketRepository ticketRepository;
    private final TokenHashService tokenHashService;
    private final Clock clock;
    private final OAuthSignupTicketProperties properties;

    @Transactional
    public String issue(String email, String name, String provider, String providerId) {
        String ticket = UUID.randomUUID().toString();
        ticketRepository.save(new OAuthSignupTicketEntity(
                tokenHashService.hashSha256(ticket),
                email,
                name,
                provider,
                providerId,
                now().plus(properties.getTtl())));
        return ticket;
    }

    public OAuthSignupTicketResponse getResponse(String ticket) {
        OAuthSignupTicket payload = getActive(ticket, false);
        return OAuthSignupTicketResponse.builder()
                .email(payload.email())
                .name(payload.name())
                .provider(payload.provider())
                .build();
    }

    @Transactional
    public OAuthSignupTicket consume(String ticket) {
        OAuthSignupTicketEntity entity = getActiveEntity(ticket, true);
        OAuthSignupTicket payload = toPayload(entity);
        ticketRepository.delete(entity);
        return payload;
    }

    @Transactional
    public void revoke(String ticket) {
        if (!org.springframework.util.StringUtils.hasText(ticket)) {
            return;
        }
        final String normalizedTicket;
        try {
            normalizedTicket = normalizeTicket(ticket);
        } catch (BusinessException ignored) {
            return;
        }
        ticketRepository.deleteByTicketHash(tokenHashService.hashSha256(normalizedTicket));
    }

    @Transactional
    public int deleteExpiredTickets() {
        return ticketRepository.deleteExpiredBatch(now(), CLEANUP_BATCH_SIZE);
    }

    private OAuthSignupTicket getActive(String ticket, boolean forUpdate) {
        return toPayload(getActiveEntity(ticket, forUpdate));
    }

    private OAuthSignupTicketEntity getActiveEntity(String ticket, boolean forUpdate) {
        String normalizedTicket = normalizeTicket(ticket);
        String ticketHash = tokenHashService.hashSha256(normalizedTicket);
        OAuthSignupTicketEntity entity = (forUpdate
                ? ticketRepository.findByTicketHashForUpdate(ticketHash)
                : ticketRepository.findById(ticketHash))
                .orElseThrow(this::invalidTicket);
        if (entity.isExpiredAt(now())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return entity;
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

    private OAuthSignupTicket toPayload(OAuthSignupTicketEntity entity) {
        return new OAuthSignupTicket(
                entity.getEmail(),
                entity.getDisplayName(),
                entity.getProvider(),
                entity.getProviderId());
    }

    private BusinessException invalidTicket() {
        return new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public record OAuthSignupTicket(String email, String name, String provider, String providerId) {
    }
}
