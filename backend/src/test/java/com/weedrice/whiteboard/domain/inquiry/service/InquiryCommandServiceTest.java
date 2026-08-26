package com.weedrice.whiteboard.domain.inquiry.service;

import com.weedrice.whiteboard.domain.inquiry.dto.InquiryCreateRequest;
import com.weedrice.whiteboard.domain.inquiry.dto.InquiryMessageCreateRequest;
import com.weedrice.whiteboard.domain.inquiry.entity.Inquiry;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryCategory;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryMessage;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryStatus;
import com.weedrice.whiteboard.domain.inquiry.port.InquiryFilePort;
import com.weedrice.whiteboard.domain.inquiry.port.InquiryNotificationPort;
import com.weedrice.whiteboard.domain.inquiry.port.InquiryUserPort;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryHistoryRepository;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryMessageRepository;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.SuperAdminPolicy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
class InquiryCommandServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
    @Mock private InquiryRepository inquiryRepository;
    @Mock private InquiryMessageRepository messageRepository;
    @Mock private InquiryHistoryRepository historyRepository;
    @Mock private InquiryUserPort userPort;
    @Mock private InquiryFilePort filePort;
    @Mock private InquiryReadService readService;
    @Mock private InquiryNotificationPort notificationPort;
    @Mock private SuperAdminPolicy superAdminPolicy;

    @Test
    void locksAuthorAndRejectsCreationAtFiveActiveInquiries() {
        when(userPort.lockActiveUserId(11L)).thenReturn(11L);
        when(inquiryRepository.countByAuthorUserIdAndStatusIn(any(), any())).thenReturn(5L);
        InquiryCommandService service = new InquiryCommandService(
                inquiryRepository, messageRepository, historyRepository, userPort,
                filePort, readService, notificationPort, superAdminPolicy,
                new SimpleMeterRegistry(), Clock.systemUTC());
        InquiryCreateRequest request = new InquiryCreateRequest(
                InquiryCategory.ACCOUNT, "Cannot sign in", "Please help", List.of());

        assertThatThrownBy(() -> service.create(11L, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INQUIRY_ACTIVE_LIMIT_EXCEEDED));

        verify(userPort).lockActiveUserId(11L);
        verify(inquiryRepository, never()).save(any());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void addingMessageUsesForceIncrementCommandLookupAndAdvancesVisibleActivity() {
        Inquiry inquiry = new Inquiry(11L, InquiryCategory.SERVICE_USE, "Question",
                LocalDateTime.of(2026, 8, 24, 0, 0));
        ReflectionTestUtils.setField(inquiry, "inquiryId", 41L);
        when(inquiryRepository.findByIdForCommand(41L)).thenReturn(Optional.of(inquiry));
        when(messageRepository.save(any(InquiryMessage.class))).thenAnswer(invocation -> {
            InquiryMessage message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "messageId", 51L);
            return message;
        });
        InquiryCommandService service = new InquiryCommandService(
                inquiryRepository, messageRepository, historyRepository, userPort,
                filePort, readService, notificationPort, superAdminPolicy,
                new SimpleMeterRegistry(), FIXED_CLOCK);

        service.addUserMessage(11L, 41L, new InquiryMessageCreateRequest("More details", List.of()));

        verify(inquiryRepository).findByIdForCommand(41L);
        assertThat(inquiry.getLastPublicActivityAt())
                .isEqualTo(LocalDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC));
        verify(filePort).associateMessageFiles(List.of(), 11L, 51L, 5);
    }

    @Test
    void locksAuthorAndRejectsAdminReopenAtFiveActiveInquiries() {
        Inquiry closedInquiry = new Inquiry(11L, InquiryCategory.ACCOUNT, "Closed question",
                LocalDateTime.of(2026, 8, 20, 0, 0));
        ReflectionTestUtils.setField(closedInquiry, "inquiryId", 41L);
        ReflectionTestUtils.setField(closedInquiry, "status", InquiryStatus.CLOSED);
        when(inquiryRepository.findById(41L)).thenReturn(Optional.of(closedInquiry));
        when(userPort.lockUserId(11L)).thenReturn(11L);
        when(inquiryRepository.findByIdForCommand(41L)).thenReturn(Optional.of(closedInquiry));
        when(inquiryRepository.countByAuthorUserIdAndStatusIn(any(), any())).thenReturn(5L);
        InquiryCommandService service = new InquiryCommandService(
                inquiryRepository, messageRepository, historyRepository, userPort,
                filePort, readService, notificationPort, superAdminPolicy,
                new SimpleMeterRegistry(), FIXED_CLOCK);

        assertThatThrownBy(() -> service.reopenByAdmin(99L, 41L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INQUIRY_ACTIVE_LIMIT_EXCEEDED));

        verify(userPort).lockUserId(11L);
        verify(inquiryRepository).findByIdForCommand(41L);
        assertThat(closedInquiry.getStatus()).isEqualTo(InquiryStatus.CLOSED);
        verify(historyRepository, never()).save(any());
    }
}
