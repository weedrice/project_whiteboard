package com.weedrice.whiteboard.domain.inquiry.dto;

import com.weedrice.whiteboard.domain.inquiry.entity.InquiryMessageType;

import java.time.LocalDateTime;
import java.util.List;

public record InquiryMessageResponse(
        Long messageId,
        Long authorUserId,
        String authorName,
        InquiryMessageType messageType,
        String content,
        List<InquiryAttachmentResponse> attachments,
        LocalDateTime createdAt) {
}
