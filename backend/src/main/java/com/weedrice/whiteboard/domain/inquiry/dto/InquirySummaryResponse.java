package com.weedrice.whiteboard.domain.inquiry.dto;

import com.weedrice.whiteboard.domain.inquiry.entity.InquiryCategory;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryPriority;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryStatus;

import java.time.LocalDateTime;

public record InquirySummaryResponse(
        Long inquiryId,
        InquiryCategory category,
        String title,
        InquiryStatus status,
        InquiryPriority effectivePriority,
        String lastPublicMessageSummary,
        Long authorUserId,
        String authorName,
        LocalDateTime staffActionSince,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt) {
}
