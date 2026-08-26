package com.weedrice.whiteboard.domain.inquiry.entity;

public enum InquiryMessageType {
    USER_MESSAGE,
    STAFF_REPLY,
    INTERNAL_NOTE;

    public boolean isPublic() {
        return this != INTERNAL_NOTE;
    }
}
