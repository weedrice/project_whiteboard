package com.weedrice.whiteboard.domain.inquiry.service;

import com.weedrice.whiteboard.domain.inquiry.entity.*;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryHistoryRepository;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryRepository;
import com.weedrice.whiteboard.domain.inquiry.port.InquiryNotificationPort;
import com.weedrice.whiteboard.global.lock.DomainLockManager;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
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
    private final TransactionTemplate transactionTemplate;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int closeExpiredResolvedInquiries() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cutoff = now.minusDays(RESOLVED_RETENTION_DAYS);
        List<Long> candidates = transactionTemplate.execute(status -> {
            domainLockManager.lockInquiryAutoClose();
            return inquiryRepository.findAutoCloseCandidateIds(cutoff, PageRequest.of(0, BATCH_SIZE));
        });
        int closed = 0;
        for (Long inquiryId : Objects.requireNonNull(candidates)) {
            boolean committed;
            try {
                committed = Boolean.TRUE.equals(transactionTemplate.execute(
                        status -> closeIfExpired(inquiryId, cutoff, now)));
            } catch (RuntimeException failure) {
                log.error("Failed to auto-close inquiry. inquiryId={}, exceptionType={}",
                        inquiryId, failure.getClass().getSimpleName());
                continue;
            }
            if (committed) {
                closed++;
                meterRegistry.counter("noviis.inquiry.auto_closed").increment();
            }
        }
        return closed;
    }

    private boolean closeIfExpired(Long inquiryId, LocalDateTime cutoff, LocalDateTime now) {
        Inquiry inquiry = inquiryRepository.findExpiredForUpdate(inquiryId, cutoff).orElse(null);
        if (inquiry == null) return false;
        InquiryStatus previous = inquiry.autoClose(now);
        historyRepository.save(new InquiryHistory(inquiryId, null, InquiryHistoryAction.AUTO_CLOSED,
                previous, InquiryStatus.CLOSED, null, now));
        notificationPort.notifyAuthor(null, inquiry.getAuthorUserId(), inquiryId, "notification.inquiry.autoClosed");
        return true;
    }
}
