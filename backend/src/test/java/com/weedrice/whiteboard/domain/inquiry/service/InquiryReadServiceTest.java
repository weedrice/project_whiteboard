package com.weedrice.whiteboard.domain.inquiry.service;

import com.weedrice.whiteboard.domain.inquiry.dto.InquiryDetailResponse;
import com.weedrice.whiteboard.domain.inquiry.dto.InquirySummaryResponse;
import com.weedrice.whiteboard.domain.inquiry.entity.Inquiry;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryCategory;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryHistory;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryHistoryAction;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryMessage;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryMessageType;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryPriority;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryStatus;
import com.weedrice.whiteboard.domain.inquiry.port.InquiryFilePort;
import com.weedrice.whiteboard.domain.inquiry.port.InquiryUserPort;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryHistoryRepository;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryMessageRepository;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InquiryReadServiceTest {

    @Mock
    private InquiryRepository inquiryRepository;
    @Mock
    private InquiryMessageRepository messageRepository;
    @Mock
    private InquiryHistoryRepository historyRepository;
    @Mock
    private InquiryUserPort userPort;
    @Mock
    private InquiryFilePort filePort;
    @Mock
    private InquiryPriorityPolicy priorityPolicy;
    private InquiryReadService readService;

    @BeforeEach
    void setUp() {
        readService = new InquiryReadService(
                inquiryRepository,
                messageRepository,
                historyRepository,
                userPort,
                filePort,
                priorityPolicy,
                Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC),
                new SimpleMeterRegistry());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getMine_loadsLatestPublicSummariesInOneBatch() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 25, 9, 0);
        Inquiry first = inquiry(1L, 10L, "First", now);
        Inquiry second = inquiry(2L, 10L, "Second", now);
        InquiryMessage firstLatest = message(101L, 1L, 10L, "latest-first", now);
        InquiryMessage secondLatest = message(102L, 2L, 10L, "latest-second", now);
        when(inquiryRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(messageRepository.findLatestByInquiryIdInAndMessageTypeIn(any(), any()))
                .thenReturn(List.of(firstLatest, secondLatest));
        when(priorityPolicy.resolve(any())).thenReturn(InquiryPriority.NORMAL);

        Page<InquirySummaryResponse> result = readService.getMine(10L, null, null, Pageable.unpaged());

        assertThat(result.getContent())
                .extracting(InquirySummaryResponse::lastPublicMessageSummary)
                .containsExactly("latest-first", "latest-second");
        verify(messageRepository).findLatestByInquiryIdInAndMessageTypeIn(any(), any());
        verify(messageRepository, never())
                .findByInquiryIdAndMessageTypeInOrderByCreatedAtAscMessageIdAsc(any(), any());
    }

    @Test
    void getMineDetail_loadsAllMessageAttachmentsInOneBatch() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 25, 9, 0);
        Inquiry inquiry = inquiry(1L, 10L, "Detail", now);
        InquiryMessage first = message(101L, 1L, 10L, "first", now);
        InquiryMessage second = message(102L, 1L, 10L, "second", now.plusMinutes(1));
        InquiryFilePort.MessageFile attachment = new InquiryFilePort.MessageFile(
                501L, 101L, "image.png", 100L, "image/png", "/api/v1/files/501");
        when(inquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));
        when(messageRepository.findByInquiryIdAndMessageTypeInOrderByCreatedAtAscMessageIdAsc(any(), any()))
                .thenReturn(List.of(first, second));
        when(filePort.findMessageFiles(any())).thenReturn(List.of(attachment));
        when(historyRepository.findByInquiryIdOrderByCreatedAtAscHistoryIdAsc(1L)).thenReturn(List.of());
        when(priorityPolicy.resolve(inquiry)).thenReturn(InquiryPriority.NORMAL);

        InquiryDetailResponse result = readService.getMineDetail(10L, 1L);

        assertThat(result.messages()).hasSize(2);
        assertThat(result.messages().getFirst().attachments()).singleElement()
                .extracting("fileId")
                .isEqualTo(501L);
        assertThat(result.messages().get(1).attachments()).isEmpty();
        verify(filePort).findMessageFiles(List.of(101L, 102L));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InquiryMessageType>> visibleTypes = ArgumentCaptor.forClass(List.class);
        verify(messageRepository).findByInquiryIdAndMessageTypeInOrderByCreatedAtAscMessageIdAsc(
                org.mockito.ArgumentMatchers.eq(1L), visibleTypes.capture());
        assertThat(visibleTypes.getValue())
                .containsExactly(InquiryMessageType.USER_MESSAGE, InquiryMessageType.STAFF_REPLY)
                .doesNotContain(InquiryMessageType.INTERNAL_NOTE);
    }

    @Test
    void getMineDetailHidesExistenceFromAnotherUser() {
        Inquiry inquiry = inquiry(1L, 10L, "Private", LocalDateTime.of(2026, 8, 25, 9, 0));
        when(inquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> readService.getMineDetail(20L, 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INQUIRY_NOT_FOUND));

        verify(messageRepository, never())
                .findByInquiryIdAndMessageTypeInOrderByCreatedAtAscMessageIdAsc(any(), any());
    }

    @Test
    void getMineDetailShowsAdminClosureActivityWithoutInternalReason() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 25, 9, 0);
        Inquiry inquiry = inquiry(1L, 10L, "Closed", now);
        InquiryHistory history = new InquiryHistory(
                1L,
                99L,
                InquiryHistoryAction.CLOSED_BY_ADMIN,
                InquiryStatus.IN_PROGRESS,
                InquiryStatus.CLOSED,
                "internal moderation detail",
                now.plusHours(1));
        when(inquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));
        when(messageRepository.findByInquiryIdAndMessageTypeInOrderByCreatedAtAscMessageIdAsc(any(), any()))
                .thenReturn(List.of());
        when(historyRepository.findByInquiryIdOrderByCreatedAtAscHistoryIdAsc(1L)).thenReturn(List.of(history));

        InquiryDetailResponse result = readService.getMineDetail(10L, 1L);

        assertThat(result.histories()).singleElement()
                .satisfies(item -> {
                    assertThat(item.actionType()).isEqualTo(InquiryHistoryAction.CLOSED_BY_ADMIN);
                    assertThat(item.toStatus()).isEqualTo(InquiryStatus.CLOSED);
                });
        assertThat(result.closureDetail()).isNull();
    }

    private Inquiry inquiry(Long inquiryId, Long authorUserId, String title, LocalDateTime now) {
        Inquiry inquiry = new Inquiry(authorUserId, InquiryCategory.SERVICE_USE, title, now);
        ReflectionTestUtils.setField(inquiry, "inquiryId", inquiryId);
        return inquiry;
    }

    private InquiryMessage message(
            Long messageId,
            Long inquiryId,
            Long authorUserId,
            String content,
            LocalDateTime now) {
        InquiryMessage message = new InquiryMessage(
                inquiryId,
                authorUserId,
                InquiryMessageType.USER_MESSAGE,
                content,
                now);
        ReflectionTestUtils.setField(message, "messageId", messageId);
        return message;
    }
}
