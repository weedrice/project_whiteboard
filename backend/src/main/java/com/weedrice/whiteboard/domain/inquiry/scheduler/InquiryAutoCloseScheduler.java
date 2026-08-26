package com.weedrice.whiteboard.domain.inquiry.scheduler;

import com.weedrice.whiteboard.domain.inquiry.service.InquiryAutoCloseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InquiryAutoCloseScheduler {
    private final InquiryAutoCloseService autoCloseService;

    @Scheduled(cron = "0 0 * * * *")
    public void closeExpiredResolvedInquiries() {
        int count = autoCloseService.closeExpiredResolvedInquiries();
        if (count > 0) log.info("Auto-closed resolved inquiries. count={}", count);
    }
}
