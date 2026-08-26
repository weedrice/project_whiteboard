package com.weedrice.whiteboard.domain.inquiry.service;

import com.weedrice.whiteboard.domain.inquiry.entity.Inquiry;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryCategory;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryPriority;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryStatus;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryRepository;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class InquiryRepositoryWorkflowTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 25, 12, 0);

    @Autowired private TestEntityManager entityManager;
    @Autowired private InquiryRepository inquiryRepository;

    @Test
    void commandLookupUsesForceIncrementLock() {
        Inquiry inquiry = persist(new Inquiry(1L, InquiryCategory.OTHER, "versioned", NOW));
        entityManager.flush();
        Long inquiryId = inquiry.getInquiryId();
        entityManager.clear();

        Inquiry locked = inquiryRepository.findByIdForCommand(inquiryId).orElseThrow();

        assertThat(entityManager.getEntityManager().getLockMode(locked))
                .isEqualTo(LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    @Test
    void adminDefaultQueueSortsActionableItemsByPriorityAndWaitingTime() {
        Inquiry normal = persist(new Inquiry(1L, InquiryCategory.OTHER, "normal", NOW.minusHours(2)));
        Inquiry high = persist(new Inquiry(2L, InquiryCategory.ACCOUNT, "high", NOW.minusHours(2)));
        Inquiry urgent = persist(new Inquiry(3L, InquiryCategory.OTHER, "urgent", NOW.minusHours(80)));
        Inquiry resolved = new Inquiry(4L, InquiryCategory.TECHNICAL, "resolved", NOW.minusHours(100));
        resolved.reply(NOW.minusHours(1));
        persist(resolved);
        entityManager.flush();

        var page = inquiryRepository.findAll(InquirySpecifications.adminFilters(
                null, null, null, null, null, null,
                NOW.minusHours(24), NOW.minusHours(72), NOW.minusHours(24)),
                PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(Inquiry::getInquiryId)
                .containsExactly(urgent.getInquiryId(), high.getInquiryId(), normal.getInquiryId(), resolved.getInquiryId());
    }

    @Test
    void priorityFilterMatchesComputedHighBucket() {
        Inquiry highCategory = persist(new Inquiry(1L, InquiryCategory.TECHNICAL, "high-category", NOW.minusHours(1)));
        Inquiry escalatedNormal = persist(new Inquiry(2L, InquiryCategory.OTHER, "escalated", NOW.minusHours(30)));
        persist(new Inquiry(3L, InquiryCategory.OTHER, "normal", NOW.minusHours(1)));
        persist(new Inquiry(4L, InquiryCategory.ACCOUNT, "urgent", NOW.minusHours(30)));
        entityManager.flush();

        var page = inquiryRepository.findAll(InquirySpecifications.adminFilters(
                null, null, InquiryPriority.HIGH, null, null, null,
                NOW.minusHours(24), NOW.minusHours(72), NOW.minusHours(24)),
                PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(Inquiry::getInquiryId)
                .containsExactly(escalatedNormal.getInquiryId(), highCategory.getInquiryId());
    }

    @Test
    void autoCloseCandidatesExcludeRecentReopenedAndAlreadyClosedInquiries() {
        Inquiry expired = new Inquiry(1L, InquiryCategory.OTHER, "expired", NOW.minusDays(10));
        expired.reply(NOW.minusDays(8));
        persist(expired);
        Inquiry recent = new Inquiry(2L, InquiryCategory.OTHER, "recent", NOW.minusDays(10));
        recent.reply(NOW.minusDays(6));
        persist(recent);
        Inquiry reopened = new Inquiry(3L, InquiryCategory.OTHER, "reopened", NOW.minusDays(10));
        reopened.reply(NOW.minusDays(8));
        reopened.addUserMessage(NOW.minusDays(1));
        persist(reopened);
        Inquiry closed = new Inquiry(4L, InquiryCategory.OTHER, "closed", NOW.minusDays(10));
        closed.reply(NOW.minusDays(8));
        closed.closeByUser(4L, NOW.minusDays(1));
        persist(closed);
        entityManager.flush();

        var candidates = inquiryRepository.findAutoCloseCandidates(
                NOW.minusDays(7), PageRequest.of(0, 100));

        assertThat(candidates)
                .extracting(Inquiry::getInquiryId)
                .containsExactly(expired.getInquiryId());
        assertThat(reopened.getStatus()).isEqualTo(InquiryStatus.NEW);
    }

    private Inquiry persist(Inquiry inquiry) {
        entityManager.persist(inquiry);
        return inquiry;
    }
}
