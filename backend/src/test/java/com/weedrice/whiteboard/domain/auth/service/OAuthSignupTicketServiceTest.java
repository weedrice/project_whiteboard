package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.OAuthSignupTicketResponse;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthSignupTicketServiceTest {

    private final OAuthSignupTicketService service = new OAuthSignupTicketService();

    @Test
    void issue_returnsUuidTicketAndGetResponseAcceptsTrimmedTicket() {
        String ticket = service.issue("test@example.com", "Test User", "google", "provider-1");

        assertThat(UUID.fromString(ticket).toString()).isEqualTo(ticket);

        OAuthSignupTicketResponse response = service.getResponse(" " + ticket + " ");
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getName()).isEqualTo("Test User");
        assertThat(response.getProvider()).isEqualTo("google");
    }

    @Test
    void getResponse_rejectsMalformedTicket() {
        assertThatThrownBy(() -> service.getResponse("ticket-1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void getResponse_rejectsTooLongTicket() {
        assertThatThrownBy(() -> service.getResponse("a".repeat(37)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void consume_invalidatesValidTicket() {
        String ticket = service.issue("test@example.com", "Test User", "google", "provider-1");

        OAuthSignupTicketService.OAuthSignupTicket payload = service.consume(ticket);

        assertThat(payload.email()).isEqualTo("test@example.com");
        assertThatThrownBy(() -> service.getResponse(ticket))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }
}
