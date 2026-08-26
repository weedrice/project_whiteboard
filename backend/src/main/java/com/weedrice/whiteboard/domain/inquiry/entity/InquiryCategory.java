package com.weedrice.whiteboard.domain.inquiry.entity;

public enum InquiryCategory {
    ACCOUNT,
    SERVICE_USE,
    TECHNICAL,
    CONTENT_OPERATION,
    SUGGESTION,
    OTHER;

    public boolean startsHighPriority() {
        return this == ACCOUNT || this == TECHNICAL;
    }
}
