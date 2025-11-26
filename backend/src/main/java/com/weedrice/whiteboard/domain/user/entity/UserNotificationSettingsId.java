package com.weedrice.whiteboard.domain.user.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserNotificationSettingsId implements Serializable {
    private Long userId;
    private String notificationType;
}