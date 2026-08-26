package com.weedrice.whiteboard.domain.inquiry.service;

import com.weedrice.whiteboard.domain.inquiry.entity.Inquiry;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryCategory;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryClosureReason;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryHistory;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryHistoryAction;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryStatus;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryHistoryRepository;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryRepository;
import com.weedrice.whiteboard.domain.inquiry.port.InquiryNotificationPort;
import com.weedrice.whiteboard.global.lock.DomainLockManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InquiryAutoCloseServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);

    @Mock private InquiryRepository inquiryRepository;
    @Mock private InquiryHistoryRepository historyRepository;
    @Mock private InquiryNotificationPort notificationPort;
    @Mock private DomainLockManager domainLockManager;

    @Test
    void closesAtSevenDayBoundaryRecordsHistoryAndNotifiesAuthor() {
        Inquiry inquiry = new Inquiry(11L, InquiryCategory.OTHER, "Question", NOW.minusDays(8));
        inquiry.reply(NOW.minusDays(7));
        ReflectionTestUtils.setField(inquiry, "inquiryId", 41L);
        when(inquiryRepository.findAutoCloseCandidates(eq(NOW.minusDays(7)), any(Pageable.class)))
                .thenReturn(List.of(inquiry));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        InquiryAutoCloseService service = new InquiryAutoCloseService(
                inquiryRepository, historyRepository, notificationPort,
                domainLockManager, registry, CLOCK);

        int closed = service.closeExpiredResolvedInquiries();

        assertThat(closed).isEqualTo(1);
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.CLOSED);
        assertThat(inquiry.getClosureReason()).isEqualTo(InquiryClosureReason.AUTO_CLOSED);
        verify(domainLockManager).lockInquiryAutoClose();
        ArgumentCaptor<InquiryHistory> history = ArgumentCaptor.forClass(InquiryHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getActionType()).isEqualTo(InquiryHistoryAction.AUTO_CLOSED);
        assertThat(history.getValue().getFromStatus()).isEqualTo(InquiryStatus.RESOLVED);
        verify(notificationPort).notifyAuthor(
                null, 11L, 41L, "notification.inquiry.autoClosed");
        assertThat(registry.counter("noviis.inquiry.auto_closed").count()).isEqualTo(1);
    }

    @Test
    void emptyEligibleBatchIsSafeAndProducesNoSideEffects() {
        when(inquiryRepository.findAutoCloseCandidates(eq(NOW.minusDays(7)), any(Pageable.class)))
                .thenReturn(List.of());
        InquiryAutoCloseService service = new InquiryAutoCloseService(
                inquiryRepository, historyRepository, notificationPort,
                domainLockManager, new SimpleMeterRegistry(), CLOCK);

        assertThat(service.closeExpiredResolvedInquiries()).isZero();

        verify(domainLockManager).lockInquiryAutoClose();
        verify(historyRepository, never()).save(any());
        verify(notificationPort, never()).notifyAuthor(any(), any(), any(), any());
    }
}
