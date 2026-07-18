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

    private static final int MAX_BATCHES_PER_RUN = 20;
    private final OAuthSignupTicketService ticketService;

    @Scheduled(cron = "0 10 * * * ?")
    public void deleteExpiredTickets() {
        int totalDeleted = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int deleted = ticketService.deleteExpiredTickets();
            totalDeleted += deleted;
            if (deleted < OAuthSignupTicketService.CLEANUP_BATCH_SIZE) {
                break;
            }
        }
        if (totalDeleted > 0) {
            log.info("Deleted {} expired OAuth signup ticket(s)", totalDeleted);
        }
    }
}
