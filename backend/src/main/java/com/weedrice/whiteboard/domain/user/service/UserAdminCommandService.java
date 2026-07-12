package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.moderation.service.ModerationAuditLogService;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAdminCommandService {

    private final UserLifecycleService userLifecycleService;
    private final UserReadableResolver userReadableResolver;
    private final ModerationAuditLogService moderationAuditLogService;

    @Transactional
    public void updateUserStatus(Long actorUserId, Long userId, String status, String reason) {
        userLifecycleService.updateAdminManagedStatus(actorUserId, userId, status);
        User actor = userReadableResolver.resolve(actorUserId);
        moderationAuditLogService.recordUserAction(
                actor,
                "ACTIVE".equals(status) ? ModerationAuditLogService.ACTION_USER_ACTIVATE : ModerationAuditLogService.ACTION_USER_SUSPEND,
                ModerationAuditLogService.TARGET_TYPE_USER,
                userId,
                null,
                reason);
    }

    public void updateUserStatus(Long actorUserId, Long userId, String status) {
        userLifecycleService.updateAdminManagedStatus(actorUserId, userId, status);
    }
}
