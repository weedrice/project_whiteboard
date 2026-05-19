package com.weedrice.whiteboard.domain.sanction.service;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SanctionEffectApplier {

    private final UserLifecycleService userLifecycleService;

    void apply(SanctionRequestValidator.NormalizedCommand command, User targetUser) {
        if (command.isPermanentBan()) {
            userLifecycleService.suspendUser(targetUser);
        }
    }
}
