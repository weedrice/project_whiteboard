package com.weedrice.whiteboard.domain.inquiry.entity;

public enum InquiryStatus {
    NEW,
    IN_PROGRESS,
    RESOLVED,
    CLOSED;

    public boolean isActive() {
        return this != CLOSED;
    }

    public boolean needsStaffAction() {
        return this == NEW || this == IN_PROGRESS;
    }
}
