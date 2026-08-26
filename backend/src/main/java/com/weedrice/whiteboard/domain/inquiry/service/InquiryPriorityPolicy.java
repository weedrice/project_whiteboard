package com.weedrice.whiteboard.domain.inquiry.service;

import com.weedrice.whiteboard.domain.inquiry.entity.Inquiry;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryPriority;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class InquiryPriorityPolicy {
    public static final String HIGH_HOURS_KEY = "INQUIRY_PRIORITY_HIGH_HOURS";
    public static final String URGENT_HOURS_KEY = "INQUIRY_PRIORITY_URGENT_HOURS";
    public static final String HIGH_CATEGORY_URGENT_HOURS_KEY = "INQUIRY_PRIORITY_HIGH_CATEGORY_URGENT_HOURS";
    private static final int DEFAULT_HIGH_HOURS = 24;
    private static final int DEFAULT_URGENT_HOURS = 72;
    private static final int DEFAULT_HIGH_CATEGORY_URGENT_HOURS = 24;

    private final GlobalConfigService globalConfigService;
    private final Clock clock;

    public InquiryPriority resolve(Inquiry inquiry) {
        if (inquiry == null || !inquiry.getStatus().needsStaffAction() || inquiry.getStaffActionSince() == null) {
            return null;
        }
        long waitingHours = Math.max(0, Duration.between(
                inquiry.getStaffActionSince(), LocalDateTime.now(clock)).toHours());
        Thresholds thresholds = thresholds();
        if (inquiry.getCategory().startsHighPriority()) {
            return waitingHours >= thresholds.highCategoryUrgentHours()
                    ? InquiryPriority.URGENT : InquiryPriority.HIGH;
        }
        if (waitingHours >= thresholds.urgentHours()) return InquiryPriority.URGENT;
        if (waitingHours >= thresholds.highHours()) return InquiryPriority.HIGH;
        return InquiryPriority.NORMAL;
    }

    Thresholds thresholds() {
        int highHours = readHours(HIGH_HOURS_KEY, DEFAULT_HIGH_HOURS);
        int urgentHours = readHours(URGENT_HOURS_KEY, DEFAULT_URGENT_HOURS);
        if (highHours >= urgentHours) {
            highHours = DEFAULT_HIGH_HOURS;
            urgentHours = DEFAULT_URGENT_HOURS;
        }
        int highCategoryUrgentHours = readHours(
                HIGH_CATEGORY_URGENT_HOURS_KEY, DEFAULT_HIGH_CATEGORY_URGENT_HOURS);
        return new Thresholds(highHours, urgentHours, highCategoryUrgentHours);
    }

    private int readHours(String key, int fallback) {
        return GlobalConfigService.parseIntConfigOrDefault(globalConfigService.getConfig(key), fallback, 1, 24 * 365);
    }

    record Thresholds(int highHours, int urgentHours, int highCategoryUrgentHours) {
    }
}
