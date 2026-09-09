package com.weedrice.whiteboard.domain.post.integration;

import com.weedrice.whiteboard.domain.actor.ActorUserPrincipal;
import com.weedrice.whiteboard.domain.post.port.PostUserReadPort;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PostUserReadIntegrationAdapter implements PostUserReadPort {

    private final UserRepository userRepository;
    private final UserBlockService userBlockService;

    @Override
    public ActorUserPrincipal resolve(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return new PostUserSnapshot(user.getUserId(), user.isUsableSuperAdmin());
    }

    @Override
    public ActorUserPrincipal findOrNull(Long userId) {
        return userRepository.findById(userId)
                .map(user -> (ActorUserPrincipal) new PostUserSnapshot(
                        user.getUserId(), user.isUsableSuperAdmin()))
                .orElse(null);
    }

    @Override
    public Long requireExistingUserId(Long userId) {
        return userRepository.findById(userId)
                .map(User::getUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public Long requireActiveUserId(Long userId) {
        return userRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, User.STATUS_ACTIVE)
                .map(User::getUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public boolean isBlockedEitherDirection(Long firstUserId, Long secondUserId) {
        return userBlockService.isEitherDirectionBlocked(firstUserId, secondUserId);
    }

    @Override
    public List<Long> getBlockedUserIds(Long userId) {
        return userBlockService.getBlockedUserIdsEitherDirection(userId);
    }

    @Override
    public List<Long> getBlockedUserIdsForExistingUser(Long userId) {
        return userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(userId);
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
