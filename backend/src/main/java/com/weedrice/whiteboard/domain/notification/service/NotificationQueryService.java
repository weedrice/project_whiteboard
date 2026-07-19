package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class NotificationQueryService {

    private static final int DEFAULT_NOTIFICATION_PAGE_SIZE = 20;
    private static final Sort NOTIFICATION_LIST_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("notificationId"));
    private static final Set<String> ALLOWED_NOTIFICATION_SORTS = Set.of("createdAt");

    private final NotificationRepository notificationRepository;
    private final NotificationTargetUrlResolver targetUrlResolver;
    private final NotificationActorVisibilityService actorVisibilityService;

    public NotificationResponse getNotifications(Long userId, Pageable pageable) {
        Pageable safePageable = PageRequestUtils.of(
                pageable,
                DEFAULT_NOTIFICATION_PAGE_SIZE,
                NOTIFICATION_LIST_SORT,
                ALLOWED_NOTIFICATION_SORTS);
        Page<Notification> notificationPage =
                notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(userId, safePageable);
        Map<Long, String> targetUrls = targetUrlResolver.resolveAll(notificationPage.getContent());
        Set<Long> hiddenActorUserIds = actorVisibilityService.resolveHiddenActorUserIds(
                userId,
                notificationPage.getContent());
        return NotificationResponse.from(notificationPage, targetUrls, hiddenActorUserIds);
    }

    public long getUnreadNotificationCount(Long userId) {
        return notificationRepository.countByUser_UserIdAndIsRead(userId, false);
    }
}
