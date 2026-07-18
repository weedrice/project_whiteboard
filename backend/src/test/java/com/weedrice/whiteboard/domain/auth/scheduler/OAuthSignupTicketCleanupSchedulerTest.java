package com.weedrice.whiteboard.domain.auth.scheduler;

import com.weedrice.whiteboard.domain.auth.service.OAuthSignupTicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthSignupTicketCleanupSchedulerTest {

    @Mock private OAuthSignupTicketService ticketService;

    @Test
    void deleteExpiredTickets_stopsAfterPartialBatch() {
        when(ticketService.deleteExpiredTickets())
                .thenReturn(OAuthSignupTicketService.CLEANUP_BATCH_SIZE, 7);
        OAuthSignupTicketCleanupScheduler scheduler = new OAuthSignupTicketCleanupScheduler(ticketService);

        scheduler.deleteExpiredTickets();

        verify(ticketService, times(2)).deleteExpiredTickets();
    }

    @Test
    void deleteExpiredTickets_capsWorkPerRun() {
        when(ticketService.deleteExpiredTickets()).thenReturn(OAuthSignupTicketService.CLEANUP_BATCH_SIZE);
        OAuthSignupTicketCleanupScheduler scheduler = new OAuthSignupTicketCleanupScheduler(ticketService);

        scheduler.deleteExpiredTickets();

        verify(ticketService, times(20)).deleteExpiredTickets();
    }
}
