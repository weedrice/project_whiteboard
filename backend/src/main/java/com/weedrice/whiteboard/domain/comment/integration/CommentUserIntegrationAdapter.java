package com.weedrice.whiteboard.domain.comment.integration;

import com.weedrice.whiteboard.domain.comment.port.CommentUserPort;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.domain.user.service.UserReadableResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CommentUserIntegrationAdapter implements CommentUserPort {

    private final UserReadableResolver userReadableResolver;
    private final UserBlockService userBlockService;
    private final UserBlockRepository userBlockRepository;

    @Override
    public UserReadContext resolveReadContext(Long userId) {
        User user = userReadableResolver.resolve(userId);
        List<Long> blockedIds = userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(userId);
        return new UserReadContext(
                user.getUserId(),
                user.isUsableSuperAdmin(),
                blockedIds == null || blockedIds.isEmpty() ? Set.of() : Set.copyOf(blockedIds));
    }

    @Override
    public void validateActive(Long userId) {
        userReadableResolver.resolveActive(userId);
    }

    @Override
    public boolean isEitherDirectionBlocked(Long firstUserId, Long secondUserId) {
        return userBlockRepository.existsEitherDirection(firstUserId, secondUserId);
    }
}
