package com.weedrice.whiteboard.domain.post.integration;

import com.weedrice.whiteboard.domain.actor.ActorUserPrincipal;
import com.weedrice.whiteboard.domain.post.port.PostUserWritePort;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostUserWriteIntegrationAdapter implements PostUserWritePort {

    private final UserWritableResolver userWritableResolver;
    private final SanctionService sanctionService;

    @Override
    public ActorUserPrincipal validate(Long userId) {
        return snapshot(userWritableResolver.resolve(userId));
    }

    @Override
    public ActorUserPrincipal validateForUpdate(Long userId) {
        return snapshot(userWritableResolver.resolveForUpdate(userId));
    }

    @Override
    public ActorUserPrincipal validateContentWriteForUpdate(Long userId) {
        User user = userWritableResolver.resolveForUpdate(userId);
        sanctionService.validateNotMuted(user);
        return snapshot(user);
    }

    private ActorUserPrincipal snapshot(User user) {
        return new PostUserSnapshot(user.getUserId(), user.isUsableSuperAdmin());
    }

    private record PostUserSnapshot(Long userId, boolean usableSuperAdmin) implements ActorUserPrincipal {
        @Override
        public Long getUserId() {
            return userId;
        }

        @Override
        public boolean isUsableSuperAdmin() {
            return usableSuperAdmin;
        }
    }
}
