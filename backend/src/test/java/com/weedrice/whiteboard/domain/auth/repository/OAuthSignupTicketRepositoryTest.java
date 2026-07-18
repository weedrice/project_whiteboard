package com.weedrice.whiteboard.domain.auth.repository;

import com.weedrice.whiteboard.domain.auth.entity.OAuthSignupTicketEntity;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class OAuthSignupTicketRepositoryTest {

    @Autowired private OAuthSignupTicketRepository ticketRepository;

    @Test
    void deleteExpiredBatch_deletesOnlyExpiredTicketsWithinBatchLimit() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 19, 12, 0);
        ticketRepository.save(ticket("oldest", now.minusHours(2)));
        ticketRepository.save(ticket("older", now.minusHours(1)));
        ticketRepository.save(ticket("active", now.plusHours(1)));
        ticketRepository.flush();

        int deleted = ticketRepository.deleteExpiredBatch(now, 1);
        ticketRepository.flush();

        assertThat(deleted).isEqualTo(1);
        assertThat(ticketRepository.findById("oldest")).isEmpty();
        assertThat(ticketRepository.findById("older")).isPresent();
        assertThat(ticketRepository.findById("active")).isPresent();
    }

    private OAuthSignupTicketEntity ticket(String hash, LocalDateTime expiresAt) {
        return new OAuthSignupTicketEntity(
                hash,
                hash + "@test.com",
                hash,
                "test-provider",
                hash + "-provider-id",
                expiresAt);
    }
}
