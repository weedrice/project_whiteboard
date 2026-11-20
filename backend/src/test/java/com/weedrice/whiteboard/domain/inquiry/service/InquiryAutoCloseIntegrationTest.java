package com.weedrice.whiteboard.domain.inquiry.service;

import com.weedrice.whiteboard.domain.inquiry.entity.Inquiry;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryCategory;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryStatus;
import com.weedrice.whiteboard.domain.inquiry.port.InquiryNotificationPort;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryHistoryRepository;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryRepository;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import com.weedrice.whiteboard.global.lock.DomainLockManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DataJpaTest
@Import({QuerydslConfig.class, InquiryAutoCloseService.class, InquiryAutoCloseIntegrationTest.TestBeans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InquiryAutoCloseIntegrationTest {
    @Autowired private InquiryRepository inquiries;
    @Autowired private InquiryHistoryRepository histories;
    @Autowired private InquiryAutoCloseService autoClose;
    @Autowired private TransactionTemplate transactions;
    @Autowired private MeterRegistry registry;
    @Autowired private Clock clock;
    @MockitoBean private DomainLockManager domainLockManager;
    @MockitoBean private InquiryNotificationPort notifications;

    private final List<Long> fixtureIds = new ArrayList<>();

    @AfterEach
    void removeFixtures() {
        transactions.executeWithoutResult(status -> {
            for (Long id : fixtureIds) {
                histories.deleteAll(histories.findByInquiryIdOrderByCreatedAtAscHistoryIdAsc(id));
            }
            inquiries.deleteAllByIdInBatch(fixtureIds);
        });
    }

    @Test
    void reopensAndCommitFailuresAreIsolatedFromOtherExpiredInquiries() {
        LocalDateTime now = LocalDateTime.now(clock);
        Long first = resolved("first", now.minusDays(11));
        Long reopened = resolved("reopened", now.minusDays(10));
        Long failed = resolved("failed", now.minusDays(9));
        Long last = resolved("last", now.minusDays(8));
        String event = "notification.inquiry.autoClosed";
        var counter = registry.counter("noviis.inquiry.auto_closed");
        double before = counter.count();

        try (var user = Executors.newSingleThreadExecutor()) {
            // The candidate list is fixed. The user's transaction commits before the next item is locked.
            doAnswer(call -> {
                user.submit(() -> transactions.executeWithoutResult(status ->
                        inquiries.findByIdForCommand(reopened).orElseThrow().addUserMessage(now)))
                        .get(5, TimeUnit.SECONDS);
                return null;
            }).when(notifications).notifyAuthor(null, 11L, first, event);
            doAnswer(call -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void beforeCommit(boolean readOnly) {
                        throw new IllegalStateException("Injected commit failure");
                    }
                });
                return null;
            }).when(notifications).notifyAuthor(null, 11L, failed, event);

            assertThat(autoClose.closeExpiredResolvedInquiries()).isEqualTo(2);
        }

        assertThat(counter.count() - before).isEqualTo(2);
        assertThat(inquiries.findById(first).orElseThrow().getStatus()).isEqualTo(InquiryStatus.CLOSED);
        assertThat(inquiries.findById(last).orElseThrow().getStatus()).isEqualTo(InquiryStatus.CLOSED);
        assertThat(inquiries.findById(reopened).orElseThrow().getStatus()).isEqualTo(InquiryStatus.NEW);
        assertThat(inquiries.findById(failed).orElseThrow().getStatus()).isEqualTo(InquiryStatus.RESOLVED);
        assertThat(histories.findByInquiryIdOrderByCreatedAtAscHistoryIdAsc(failed)).isEmpty();
        assertThat(histories.findByInquiryIdOrderByCreatedAtAscHistoryIdAsc(last)).hasSize(1);
        verify(notifications, never()).notifyAuthor(null, 11L, reopened, event);
    }

    private Long resolved(String title, LocalDateTime resolutionTime) {
        Inquiry inquiry = new Inquiry(11L, InquiryCategory.OTHER, title, resolutionTime.minusDays(1));
        inquiry.reply(resolutionTime);
        Long id = inquiries.saveAndFlush(inquiry).getInquiryId();
        fixtureIds.add(id);
        return id;
    }

    @TestConfiguration
    static class TestBeans {
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC); }
        @Bean MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
    }
}
