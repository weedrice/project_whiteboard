package com.weedrice.whiteboard.domain.inquiry.dto;

import com.weedrice.whiteboard.domain.inquiry.entity.InquiryHistoryAction;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryStatus;

import java.time.LocalDateTime;

public record InquiryHistoryResponse(
        Long historyId,
        InquiryHistoryAction actionType,
        InquiryStatus fromStatus,
        InquiryStatus toStatus,
        LocalDateTime createdAt) {
}
