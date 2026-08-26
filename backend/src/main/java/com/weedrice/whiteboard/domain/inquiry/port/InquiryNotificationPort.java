package com.weedrice.whiteboard.domain.inquiry.port;

public interface InquiryNotificationPort {
    void notifySuperAdmins(Long actorUserId, Long inquiryId, String messageKey);

    void notifyAuthor(Long actorUserId, Long authorUserId, Long inquiryId, String messageKey);
}
