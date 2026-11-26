package com.weedrice.whiteboard.domain.inquiry.service;

import com.weedrice.whiteboard.domain.inquiry.entity.Inquiry;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryCategory;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryHistoryAction;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryStatus;
import com.weedrice.whiteboard.domain.inquiry.port.InquiryNotificationPort;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryHistoryRepository;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import com.weedrice.whiteboard.global.lock.DomainLockManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DataJpaTest(showSql = false, properties = "spring.flyway.postgresql.transactional-lock=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("postgres-smoke")
@EnabledIfEnvironmentVariable(named = "POSTGRES_SMOKE_TEST", matches = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({QuerydslConfig.class, DomainLockManager.class})
class InquiryAutoClosePostgresTest {
    @Autowired private InquiryRepository inquiries;
    @Autowired private InquiryHistoryRepository histories;
    @Autowired private UserRepository users;
    @Autowired private DomainLockManager locks;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;

    @Test
    void autoCloseRechecksEligibilityAfterAUserReopenCommitsWhileItWaits() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2001-02-12T00:00:00Z"), ZoneOffset.UTC);
        LocalDateTime now = LocalDateTime.now(clock);
        String login = "inq-" + UUID.randomUUID().toString().substring(0, 8);
        record Fixture(Long author, Long reopened, Long expired) {}
        Fixture fixture = transactions.execute(status -> {
            Long author = users.saveAndFlush(User.builder().loginId(login).displayName(login)
                    .email(login + "@example.org").password("test-password").build()).getUserId();
            return new Fixture(author, resolved(author, now.minusDays(10)), resolved(author, now.minusDays(9)));
        });

        var registry = new SimpleMeterRegistry();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var notifications = mock(InquiryNotificationPort.class);
            var closer = new InquiryAutoCloseService(inquiries, histories, notifications, locks,
                    registry, clock, transactions);
            var closing = transactions.execute(status -> {
                inquiries.findByIdForCommand(fixture.reopened()).orElseThrow().addUserMessage(now);
                inquiries.flush();
                var result = executor.submit(closer::closeExpiredResolvedInquiries);
                // The scheduler sees the committed RESOLVED row and waits for this user's update.
                await().pollInSameThread().atMost(10, TimeUnit.SECONDS).until(() ->
                        Boolean.TRUE.equals(jdbc.queryForObject("""
                                SELECT EXISTS (SELECT FROM pg_locks
                                   WHERE NOT granted AND pg_backend_pid() = ANY(pg_blocking_pids(pid)))
                                """, Boolean.class)));
                return result;
            });

            assertThat(closing.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(inquiries.findById(fixture.reopened()).orElseThrow().getStatus()).isEqualTo(InquiryStatus.NEW);
            assertThat(inquiries.findById(fixture.expired()).orElseThrow().getStatus()).isEqualTo(InquiryStatus.CLOSED);
            assertThat(histories.findByInquiryIdOrderByCreatedAtAscHistoryIdAsc(fixture.reopened())).isEmpty();
            assertThat(histories.findByInquiryIdOrderByCreatedAtAscHistoryIdAsc(fixture.expired()))
                    .singleElement().satisfies(history -> assertThat(history.getActionType())
                            .isEqualTo(InquiryHistoryAction.AUTO_CLOSED));
            String event = "notification.inquiry.autoClosed";
            verify(notifications, never()).notifyAuthor(null, fixture.author(), fixture.reopened(), event);
            verify(notifications).notifyAuthor(null, fixture.author(), fixture.expired(), event);
            assertThat(registry.counter("noviis.inquiry.auto_closed").count()).isEqualTo(1);
        } finally {
            registry.close();
            transactions.executeWithoutResult(status -> {
                inquiries.deleteAllByIdInBatch(List.of(fixture.reopened(), fixture.expired()));
                users.deleteById(fixture.author());
            });
        }
    }

    private Long resolved(Long author, LocalDateTime time) {
        Inquiry inquiry = new Inquiry(author, InquiryCategory.OTHER, "Auto-close test", time.minusDays(1));
        inquiry.reply(time);
        return inquiries.saveAndFlush(inquiry).getInquiryId();
    }
}
