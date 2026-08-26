package com.weedrice.whiteboard.domain.inquiry.entity;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InquiryStateTest {
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 1, 9, 0);

    @Test
    void publicReplyResolvesAndUserFollowUpReopensWithNewWaitingClock() {
        Inquiry inquiry = new Inquiry(10L, InquiryCategory.SERVICE_USE, "title", CREATED_AT);
        inquiry.start();

        LocalDateTime answeredAt = CREATED_AT.plusHours(2);
        assertThat(inquiry.reply(answeredAt)).isEqualTo(InquiryStatus.IN_PROGRESS);
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.RESOLVED);
        assertThat(inquiry.getStaffActionSince()).isNull();

        LocalDateTime followedUpAt = answeredAt.plusDays(1);
        assertThat(inquiry.addUserMessage(followedUpAt)).isEqualTo(InquiryStatus.RESOLVED);
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.NEW);
        assertThat(inquiry.getStaffActionSince()).isEqualTo(followedUpAt);
    }

    @Test
    void userCanWithdrawOnlyNewAndCloseOnlyResolved() {
        Inquiry withdrawn = new Inquiry(10L, InquiryCategory.OTHER, "title", CREATED_AT);
        withdrawn.withdraw(10L, CREATED_AT.plusMinutes(1));
        assertThat(withdrawn.getStatus()).isEqualTo(InquiryStatus.CLOSED);
        assertThat(withdrawn.getClosureReason()).isEqualTo(InquiryClosureReason.WITHDRAWN);

        Inquiry unresolved = new Inquiry(10L, InquiryCategory.OTHER, "title", CREATED_AT);
        assertThatThrownBy(() -> unresolved.closeByUser(10L, CREATED_AT.plusMinutes(1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INQUIRY_STATE));
    }

    @Test
    void closedInquiryAllowsAdminReopenButRejectsPublicMessages() {
        Inquiry inquiry = new Inquiry(10L, InquiryCategory.TECHNICAL, "title", CREATED_AT);
        inquiry.closeByAdmin(99L, CREATED_AT.plusHours(1));

        assertThatThrownBy(() -> inquiry.addUserMessage(CREATED_AT.plusHours(2)))
                .isInstanceOf(BusinessException.class);

        inquiry.reopenByAdmin(CREATED_AT.plusHours(3));
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.IN_PROGRESS);
        assertThat(inquiry.getClosureReason()).isNull();
        assertThat(inquiry.getStaffActionSince()).isEqualTo(CREATED_AT.plusHours(3));
    }

    @Test
    void everyPublicMessageAdvancesPublicActivityWithoutResettingExistingWaitClock() {
        Inquiry inquiry = new Inquiry(10L, InquiryCategory.SERVICE_USE, "title", CREATED_AT);
        LocalDateTime userMessageAt = CREATED_AT.plusHours(2);

        inquiry.addUserMessage(userMessageAt);

        assertThat(inquiry.getLastPublicActivityAt()).isEqualTo(userMessageAt);
        assertThat(inquiry.getStaffActionSince()).isEqualTo(CREATED_AT);

        LocalDateTime replyAt = CREATED_AT.plusHours(3);
        inquiry.reply(replyAt);
        assertThat(inquiry.getLastPublicActivityAt()).isEqualTo(replyAt);
    }

    @Test
    void autoCloseRejectsNewAndAlreadyClosedInquiries() {
        Inquiry newInquiry = new Inquiry(10L, InquiryCategory.OTHER, "title", CREATED_AT);
        assertThatThrownBy(() -> newInquiry.autoClose(CREATED_AT.plusDays(7)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INQUIRY_STATE));

        Inquiry closedInquiry = new Inquiry(10L, InquiryCategory.OTHER, "title", CREATED_AT);
        closedInquiry.closeByAdmin(99L, CREATED_AT.plusHours(1));
        assertThatThrownBy(() -> closedInquiry.autoClose(CREATED_AT.plusDays(7)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INQUIRY_STATE));
    }
}
