package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class NotificationReadCommandService {

    private final UserRepository userRepository;
    private final NotificationCommandService commandService;

    NotificationReadCommandService(UserRepository userRepository,
                                   NotificationCommandService commandService) {
        this.userRepository = userRepository;
        this.commandService = commandService;
    }

    @Transactional
    public void readNotification(Long userId, Long notificationId) {
        commandService.readNotification(userId, notificationId);
    }

    @Transactional
    public void readAllNotifications(Long userId) {
        validateUserExists(userId);
        commandService.readAllNotifications(userId);
    }

    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }
}
