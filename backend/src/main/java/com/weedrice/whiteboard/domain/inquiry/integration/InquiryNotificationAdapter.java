package com.weedrice.whiteboard.domain.inquiry.integration;

import com.weedrice.whiteboard.domain.inquiry.port.InquiryNotificationPort;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.service.NotificationService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class InquiryNotificationAdapter implements InquiryNotificationPort {
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final GlobalConfigService globalConfigService;

    @Override
    public void notifySuperAdmins(Long actorUserId, Long inquiryId, String messageKey) {
        User actor = findUser(actorUserId);
        for (User receiver : userRepository.findUsableSuperAdmins()) {
            publish(receiver, actor, inquiryId, messageKey);
        }
    }

    @Override
    public void notifyAuthor(Long actorUserId, Long authorUserId, Long inquiryId, String messageKey) {
        publish(findUser(authorUserId), findUser(actorUserId), inquiryId, messageKey);
    }

    private void publish(User receiver, User actor, Long inquiryId, String messageKey) {
        NotificationType notificationType = globalConfigService.isInquiryNotificationTypeEnabled()
                ? NotificationType.INQUIRY
                : NotificationType.SYSTEM;
        notificationService.handleNotificationEvent(NotificationEvent.inAppOnlyLocalized(
                receiver, actor, notificationType, NotificationSourceType.INQUIRY,
                inquiryId, messageKey));
    }

    private User findUser(Long userId) {
        return userId == null ? null : userRepository.findById(userId).orElse(null);
    }
}
