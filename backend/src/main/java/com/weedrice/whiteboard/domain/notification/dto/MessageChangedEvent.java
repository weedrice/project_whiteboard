package com.weedrice.whiteboard.domain.notification.dto;

/** Private mailbox invalidation signal. Never carries message text or sender profile data. */
public record MessageChangedEvent(Long messageId, Long partnerId) {
}
