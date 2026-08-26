package com.weedrice.whiteboard.domain.inquiry.dto;

public record InquiryAttachmentResponse(
        Long fileId,
        String originalName,
        Long fileSize,
        String mimeType,
        String url) {
}
