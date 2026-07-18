package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
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
    private final UserBlockRepository userBlockRepository;

    public NotificationResponse getNotifications(Long userId, Pageable pageable) {
        Pageable safePageable = PageRequestUtils.of(
                pageable,
                DEFAULT_NOTIFICATION_PAGE_SIZE,
                NOTIFICATION_LIST_SORT,
                ALLOWED_NOTIFICATION_SORTS);
        Page<Notification> notificationPage =
                notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(userId, safePageable);
        Map<Long, String> targetUrls = targetUrlResolver.resolveAll(notificationPage.getContent());
        Set<Long> hiddenActorUserIds = resolveHiddenActorUserIds(userId, notificationPage);
        return NotificationResponse.from(notificationPage, targetUrls, hiddenActorUserIds);
    }

    private Set<Long> resolveHiddenActorUserIds(Long userId, Page<Notification> notificationPage) {
        List<Long> blockedUserIds = userBlockRepository.findBlockedUserIdsEitherDirectionByUserId(userId);
        Set<Long> hiddenActorUserIds = blockedUserIds == null
                ? new HashSet<>()
                : new HashSet<>(blockedUserIds);
        notificationPage.getContent().stream()
                .map(Notification::getActor)
                .filter(java.util.Objects::nonNull)
                .filter(actor -> !User.STATUS_ACTIVE.equals(actor.getStatus()) || actor.getDeletedAt() != null)
                .map(User::getUserId)
                .forEach(hiddenActorUserIds::add);
        return hiddenActorUserIds;
    }

    public long getUnreadNotificationCount(Long userId) {
        return notificationRepository.countByUser_UserIdAndIsRead(userId, false);
    }
}
