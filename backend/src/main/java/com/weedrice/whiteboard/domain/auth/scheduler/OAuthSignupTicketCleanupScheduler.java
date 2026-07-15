package com.weedrice.whiteboard.domain.auth.scheduler;

import com.weedrice.whiteboard.domain.auth.service.OAuthSignupTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthSignupTicketCleanupScheduler {

    private final OAuthSignupTicketService ticketService;

    @Scheduled(cron = "0 10 * * * ?")
    public void deleteExpiredTickets() {
        int deleted = ticketService.deleteExpiredTickets();
        if (deleted > 0) {
            log.info("Deleted {} expired OAuth signup ticket(s)", deleted);
        }
    }
}
