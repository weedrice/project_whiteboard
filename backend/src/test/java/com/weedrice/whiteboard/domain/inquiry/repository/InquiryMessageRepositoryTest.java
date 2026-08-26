package com.weedrice.whiteboard.domain.inquiry.repository;

import com.weedrice.whiteboard.domain.inquiry.entity.Inquiry;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryCategory;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryMessage;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryMessageType;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class InquiryMessageRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private InquiryMessageRepository messageRepository;

    @Test
    void findLatestByInquiryIdInAndMessageTypeIn_returnsOneLatestPublicMessagePerInquiry() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 25, 10, 0);
        Inquiry firstInquiry = persistInquiry(10L, "First", base);
        Inquiry secondInquiry = persistInquiry(20L, "Second", base);

        persistMessage(firstInquiry, 10L, InquiryMessageType.USER_MESSAGE, "old", base);
        persistMessage(firstInquiry, 99L, InquiryMessageType.INTERNAL_NOTE, "internal", base.plusHours(3));
        persistMessage(firstInquiry, 99L, InquiryMessageType.STAFF_REPLY, "latest-public", base.plusHours(1));
        persistMessage(secondInquiry, 20L, InquiryMessageType.USER_MESSAGE, "second-latest", base.plusHours(2));
        entityManager.flush();

        List<InquiryMessage> messages = messageRepository.findLatestByInquiryIdInAndMessageTypeIn(
                List.of(firstInquiry.getInquiryId(), secondInquiry.getInquiryId()),
                List.of(InquiryMessageType.USER_MESSAGE, InquiryMessageType.STAFF_REPLY));

        assertThat(messages)
                .extracting(InquiryMessage::getContent)
                .containsExactlyInAnyOrder("latest-public", "second-latest");
    }

    @Test
    void findLatestByInquiryIdInAndMessageTypeIn_usesMessageIdAsTimestampTieBreaker() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 25, 10, 0);
        Inquiry inquiry = persistInquiry(10L, "Tie", now);
        persistMessage(inquiry, 10L, InquiryMessageType.USER_MESSAGE, "first", now);
        persistMessage(inquiry, 10L, InquiryMessageType.USER_MESSAGE, "second", now);
        entityManager.flush();

        List<InquiryMessage> messages = messageRepository.findLatestByInquiryIdInAndMessageTypeIn(
                List.of(inquiry.getInquiryId()),
                List.of(InquiryMessageType.USER_MESSAGE, InquiryMessageType.STAFF_REPLY));

        assertThat(messages).singleElement()
                .extracting(InquiryMessage::getContent)
                .isEqualTo("second");
    }

    private Inquiry persistInquiry(Long authorUserId, String title, LocalDateTime now) {
        Inquiry inquiry = new Inquiry(authorUserId, InquiryCategory.SERVICE_USE, title, now);
        entityManager.persist(inquiry);
        return inquiry;
    }

    private void persistMessage(
            Inquiry inquiry,
            Long authorUserId,
            InquiryMessageType type,
            String content,
            LocalDateTime now) {
        entityManager.persist(new InquiryMessage(inquiry.getInquiryId(), authorUserId, type, content, now));
    }
}
