package com.weedrice.whiteboard.domain.user.integration;

import com.weedrice.whiteboard.domain.post.scheduled.service.ScheduledPostWithdrawalService;
import com.weedrice.whiteboard.domain.user.port.UserScheduledPostLifecyclePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserScheduledPostLifecycleIntegrationAdapter implements UserScheduledPostLifecyclePort {
    private final ScheduledPostWithdrawalService withdrawalService;

    @Override
    public void cancelUnpublishedForLockedUser(Long userId) {
        withdrawalService.cancelUnpublishedForLockedUser(userId);
    }
}
