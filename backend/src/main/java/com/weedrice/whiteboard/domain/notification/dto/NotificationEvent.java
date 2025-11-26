package com.weedrice.whiteboard.domain.notification.dto;

import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NotificationEvent {
    private User userToNotify; // 알림을 받을 사용자
    private User actor; // 알림을 발생시킨 사용자
    private String notificationType; // COMMENT, LIKE 등
    private String sourceType; // POST, COMMENT 등
    private Long sourceId;
    private String content;
}
