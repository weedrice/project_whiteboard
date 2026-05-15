package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional(readOnly = true)
class NotificationQueryService {

    private static final int DEFAULT_NOTIFICATION_PAGE_SIZE = 20;
    private static final Sort NOTIFICATION_LIST_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("notificationId"));
    private static final Set<String> ALLOWED_NOTIFICATION_SORTS = Set.of("createdAt");

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    NotificationQueryService(NotificationRepository notificationRepository,
                             UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    public NotificationResponse getNotifications(Long userId, Pageable pageable) {
        validateUserExists(userId);
        Pageable safePageable = PageRequestUtils.of(
                pageable,
                DEFAULT_NOTIFICATION_PAGE_SIZE,
                NOTIFICATION_LIST_SORT,
                ALLOWED_NOTIFICATION_SORTS);
        Page<Notification> notificationPage =
                notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(userId, safePageable);
        return NotificationResponse.from(notificationPage);
    }

    public long getUnreadNotificationCount(Long userId) {
        validateUserExists(userId);
        return notificationRepository.countByUser_UserIdAndIsRead(userId, false);
    }

    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }
}
