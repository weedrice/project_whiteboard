package com.weedrice.whiteboard.domain.inquiry.dto;

import com.weedrice.whiteboard.domain.inquiry.entity.InquiryCategory;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryClosureReason;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryPriority;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryStatus;

import java.time.LocalDateTime;
import java.util.List;

public record InquiryDetailResponse(
        Long inquiryId,
        Long authorUserId,
        String authorName,
        InquiryCategory category,
        String title,
        InquiryStatus status,
        InquiryPriority effectivePriority,
        InquiryClosureReason closureReason,
        String closureDetail,
        InquiryAllowedActions allowedActions,
        List<InquiryMessageResponse> messages,
        List<InquiryHistoryResponse> histories,
        LocalDateTime firstRespondedAt,
        LocalDateTime resolvedAt,
        LocalDateTime closedAt,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt) {
}
