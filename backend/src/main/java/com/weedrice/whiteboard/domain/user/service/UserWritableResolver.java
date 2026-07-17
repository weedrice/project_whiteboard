package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UserWritableResolver {

    private final UserRepository userRepository;
    private final SanctionService sanctionService;

    public User resolve(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validateActiveAccount(user);
        sanctionService.validateNotBanned(user);
        return user;
    }

    public User resolveForUpdate(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validateActiveAccount(user);
        sanctionService.validateNotBanned(user);
        return user;
    }

    public List<User> resolveForUpdateWithRelatedUsers(Long userId, Collection<Long> relatedUserIds) {
        LinkedHashSet<Long> userIds = new LinkedHashSet<>();
        userIds.add(userId);
        if (relatedUserIds != null) {
            userIds.addAll(relatedUserIds);
        }

        List<User> lockedUsers = userRepository.findAllByIdForUpdate(userIds);
        User currentUser = lockedUsers.stream()
                .filter(user -> userId.equals(user.getUserId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validateActiveAccount(currentUser);
        sanctionService.validateNotBanned(currentUser);
        return lockedUsers;
    }

    public List<User> lockExistingUsersForUpdate(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllByIdForUpdate(new LinkedHashSet<>(userIds));
    }

    public LockedUserPair lockUserPairForUpdate(Long firstUserId, Long secondUserId) {
        List<Long> orderedUserIds = List.of(firstUserId, secondUserId).stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        List<User> lockedUsers = userRepository.findAllByIdForUpdate(orderedUserIds);
        if (lockedUsers.size() != orderedUserIds.size()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        User firstUser = findLockedUser(lockedUsers, firstUserId);
        User secondUser = firstUserId.equals(secondUserId)
                ? firstUser
                : findLockedUser(lockedUsers, secondUserId);
        return new LockedUserPair(firstUser, secondUser);
    }

    public User validateWritable(User user) {
        validateActiveAccount(user);
        sanctionService.validateNotBanned(user);
        return user;
    }

    private User findLockedUser(List<User> lockedUsers, Long userId) {
        return lockedUsers.stream()
                .filter(user -> userId.equals(user.getUserId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void validateActiveAccount(User user) {
        if (!user.isActiveAccount()) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
    }

    public record LockedUserPair(User firstUser, User secondUser) {
    }
}
