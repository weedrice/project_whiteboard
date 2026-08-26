package com.weedrice.whiteboard.domain.inquiry.service;

import com.weedrice.whiteboard.domain.inquiry.entity.*;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryHistoryRepository;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryRepository;
import com.weedrice.whiteboard.domain.inquiry.port.InquiryNotificationPort;
import com.weedrice.whiteboard.global.lock.DomainLockManager;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InquiryAutoCloseService {
    private static final int BATCH_SIZE = 100;
    private static final int RESOLVED_RETENTION_DAYS = 7;

    private final InquiryRepository inquiryRepository;
    private final InquiryHistoryRepository historyRepository;
    private final InquiryNotificationPort notificationPort;
    private final DomainLockManager domainLockManager;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    @Transactional
    public int closeExpiredResolvedInquiries() {
        domainLockManager.lockInquiryAutoClose();
        LocalDateTime now = LocalDateTime.now(clock);
        var candidates = inquiryRepository.findAutoCloseCandidates(
                now.minusDays(RESOLVED_RETENTION_DAYS), PageRequest.of(0, BATCH_SIZE));
        for (Inquiry inquiry : candidates) {
            InquiryStatus previous = inquiry.autoClose(now);
            historyRepository.save(new InquiryHistory(inquiry.getInquiryId(), null,
                    InquiryHistoryAction.AUTO_CLOSED, previous, InquiryStatus.CLOSED, null, now));
            meterRegistry.counter("noviis.inquiry.auto_closed").increment();
            notificationPort.notifyAuthor(null, inquiry.getAuthorUserId(), inquiry.getInquiryId(),
                    "notification.inquiry.autoClosed");
        }
        return candidates.size();
    }
}
