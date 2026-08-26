package com.weedrice.whiteboard.domain.inquiry.service;

import com.weedrice.whiteboard.domain.inquiry.entity.Inquiry;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryCategory;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryPriority;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InquiryPriorityPolicyTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
    private final GlobalConfigService globalConfigService = mock(GlobalConfigService.class);
    private final InquiryPriorityPolicy policy = new InquiryPriorityPolicy(globalConfigService, CLOCK);
    private final LocalDateTime now = LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);

    @Test
    void normalCategoryEscalatesAtTwentyFourAndSeventyTwoHours() {
        assertThat(policy.resolve(inquiry(InquiryCategory.OTHER, now.minusHours(23)))).isEqualTo(InquiryPriority.NORMAL);
        assertThat(policy.resolve(inquiry(InquiryCategory.OTHER, now.minusHours(24)))).isEqualTo(InquiryPriority.HIGH);
        assertThat(policy.resolve(inquiry(InquiryCategory.OTHER, now.minusHours(72)))).isEqualTo(InquiryPriority.URGENT);
    }

    @Test
    void accountAndTechnicalStartHighAndBecomeUrgentAtTwentyFourHours() {
        assertThat(policy.resolve(inquiry(InquiryCategory.ACCOUNT, now))).isEqualTo(InquiryPriority.HIGH);
        assertThat(policy.resolve(inquiry(InquiryCategory.TECHNICAL, now.minusHours(24))))
                .isEqualTo(InquiryPriority.URGENT);
    }

    @Test
    void resolvedInquiryHasNoEffectivePriority() {
        Inquiry inquiry = inquiry(InquiryCategory.ACCOUNT, now.minusDays(2));
        inquiry.reply(now.minusHours(1));
        assertThat(policy.resolve(inquiry)).isNull();
    }

    @Test
    void invalidPersistedThresholdRelationshipFallsBackToSafeDefaults() {
        when(globalConfigService.getConfig(InquiryPriorityPolicy.HIGH_HOURS_KEY)).thenReturn("96");
        when(globalConfigService.getConfig(InquiryPriorityPolicy.URGENT_HOURS_KEY)).thenReturn("72");

        InquiryPriorityPolicy.Thresholds thresholds = policy.thresholds();

        assertThat(thresholds.highHours()).isEqualTo(24);
        assertThat(thresholds.urgentHours()).isEqualTo(72);
    }

    private Inquiry inquiry(InquiryCategory category, LocalDateTime createdAt) {
        return new Inquiry(1L, category, "title", createdAt);
    }
}
