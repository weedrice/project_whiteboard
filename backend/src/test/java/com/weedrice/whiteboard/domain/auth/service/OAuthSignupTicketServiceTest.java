package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.OAuthSignupTicketResponse;
import com.weedrice.whiteboard.domain.auth.entity.OAuthSignupTicketEntity;
import com.weedrice.whiteboard.domain.auth.repository.OAuthSignupTicketRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthSignupTicketServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private OAuthSignupTicketRepository ticketRepository;

    private TokenHashService tokenHashService;
    private OAuthSignupTicketService service;

    @BeforeEach
    void setUp() {
        tokenHashService = new TokenHashService();
        service = new OAuthSignupTicketService(ticketRepository, tokenHashService, CLOCK);
    }

    @Test
    void issue_returnsUuidAndStoresOnlyHashWithTenMinuteExpiry() {
        String ticket = service.issue("test@example.com", "Test User", "google", "provider-1");

        assertThat(UUID.fromString(ticket).toString()).isEqualTo(ticket);
        ArgumentCaptor<OAuthSignupTicketEntity> entityCaptor = ArgumentCaptor.forClass(OAuthSignupTicketEntity.class);
        verify(ticketRepository).save(entityCaptor.capture());
        OAuthSignupTicketEntity stored = entityCaptor.getValue();
        assertThat(stored.getTicketHash()).isEqualTo(tokenHashService.hashSha256(ticket));
        assertThat(stored.getTicketHash()).doesNotContain(ticket);
        assertThat(stored.getExpiresAt()).isEqualTo(LocalDateTime.now(CLOCK).plusMinutes(10));
    }

    @Test
    void getResponse_acceptsTrimmedActiveTicket() {
        String ticket = UUID.randomUUID().toString();
        String hash = tokenHashService.hashSha256(ticket);
        when(ticketRepository.findById(hash)).thenReturn(Optional.of(activeEntity(hash)));

        OAuthSignupTicketResponse response = service.getResponse(" " + ticket + " ");

        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getName()).isEqualTo("Test User");
        assertThat(response.getProvider()).isEqualTo("google");
    }

    @Test
    void getResponse_rejectsMalformedTicketWithoutRepositoryLookup() {
        assertThatThrownBy(() -> service.getResponse("ticket-1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        verify(ticketRepository, never()).findById(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getResponse_rejectsExpiredTicket() {
        String ticket = UUID.randomUUID().toString();
        String hash = tokenHashService.hashSha256(ticket);
        OAuthSignupTicketEntity expired = new OAuthSignupTicketEntity(
                hash, "test@example.com", null, "google", "provider-1", LocalDateTime.now(CLOCK));
        when(ticketRepository.findById(hash)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.getResponse(ticket))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void consume_locksAndDeletesActiveTicket() {
        String ticket = UUID.randomUUID().toString();
        String hash = tokenHashService.hashSha256(ticket);
        OAuthSignupTicketEntity entity = activeEntity(hash);
        when(ticketRepository.findByTicketHashForUpdate(hash)).thenReturn(Optional.of(entity));

        OAuthSignupTicketService.OAuthSignupTicket payload = service.consume(ticket);

        assertThat(payload.providerId()).isEqualTo("provider-1");
        verify(ticketRepository).delete(entity);
    }

    @Test
    void consume_rejectsMalformedTicketWithoutRepositoryLookup() {
        assertThatThrownBy(() -> service.consume("forged-ticket"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(ticketRepository, never()).findByTicketHashForUpdate(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void consume_rejectsExpiredTicket() {
        String ticket = UUID.randomUUID().toString();
        String hash = tokenHashService.hashSha256(ticket);
        OAuthSignupTicketEntity expired = new OAuthSignupTicketEntity(
                hash, "test@example.com", null, "google", "provider-1", LocalDateTime.now(CLOCK));
        when(ticketRepository.findByTicketHashForUpdate(hash)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.consume(ticket))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        verify(ticketRepository, never()).delete(expired);
    }

    @Test
    void consume_rejectsReplayedTicketAfterFirstConsumption() {
        String ticket = UUID.randomUUID().toString();
        String hash = tokenHashService.hashSha256(ticket);
        OAuthSignupTicketEntity entity = activeEntity(hash);
        when(ticketRepository.findByTicketHashForUpdate(hash))
                .thenReturn(Optional.of(entity))
                .thenReturn(Optional.empty());

        service.consume(ticket);

        assertThatThrownBy(() -> service.consume(ticket))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        verify(ticketRepository).delete(entity);
    }

    @Test
    void deleteExpiredTickets_usesCurrentClockTime() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        when(ticketRepository.deleteExpiredAtOrBefore(now)).thenReturn(3);

        assertThat(service.deleteExpiredTickets()).isEqualTo(3);
    }

    private OAuthSignupTicketEntity activeEntity(String hash) {
        return new OAuthSignupTicketEntity(
                hash,
                "test@example.com",
                "Test User",
                "google",
                "provider-1",
                LocalDateTime.now(CLOCK).plusMinutes(1));
    }
}
