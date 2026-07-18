package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.user.dto.BlockedUserResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserBlock;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.notification.service.NotificationAccessInvalidationService;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserBlockService {

    private static final int DEFAULT_BLOCK_PAGE_SIZE = 20;

    private final UserBlockRepository userBlockRepository;
    private final UserReadableResolver userReadableResolver;
    private final UserWritableResolver userWritableResolver;
    private final NotificationAccessInvalidationService notificationAccessInvalidationService;

    @Transactional
    public void blockUser(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_BLOCK_SELF);
        }

        UserWritableResolver.LockedUserPair lockedUsers =
                userWritableResolver.lockUserPairForUpdate(userId, targetUserId);
        User user = userWritableResolver.validateWritable(lockedUsers.firstUser());
        User target = lockedUsers.secondUser();
        if (!target.isActiveAccount()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        UserBlock userBlock = UserBlock.builder()
                .user(user)
                .target(target)
                .build();

        try {
            userBlockRepository.saveAndFlush(userBlock);
            notificationAccessInvalidationService.invalidateCommentTopicsForUsersAfterCommit(userId, targetUserId);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.ALREADY_BLOCKED);
        }
    }

    @Transactional
    public void unblockUser(Long userId, Long targetUserId) {
        UserWritableResolver.LockedUserPair lockedUsers =
                userWritableResolver.lockUserPairForUpdate(userId, targetUserId);
        User user = userWritableResolver.validateWritable(lockedUsers.firstUser());
        User target = lockedUsers.secondUser();

        UserBlock userBlock = userBlockRepository.findByUserAndTarget(user, target)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        userBlockRepository.delete(userBlock);
        notificationAccessInvalidationService.invalidateCommentTopicsForUsersAfterCommit(userId, targetUserId);
    }

    public Page<BlockedUserResponse> getBlockedUsers(Long userId, Pageable pageable) {
        User user = userReadableResolver.resolve(userId);

        Pageable safePageable = PageRequestUtils.of(pageable, DEFAULT_BLOCK_PAGE_SIZE);
        Page<UserBlock> blocks = userBlockRepository.findPageByUserWithTarget(user, safePageable);

        return blocks.map(block -> new BlockedUserResponse(
                block.getTarget().getUserId(),
                block.getTarget().getLoginId(),
                block.getTarget().getDisplayName(),
                block.getCreatedAt()));
    }

    public boolean isBlocked(Long userId, Long targetUserId) {
        validateUserExists(userId);
        validateUserExists(targetUserId);
        return userBlockRepository.existsByUser_UserIdAndTarget_UserId(userId, targetUserId);
    }

    public boolean hasBlockFromReporterToTarget(Long userId, Long targetUserId) {
        return userBlockRepository.existsByUser_UserIdAndTarget_UserId(userId, targetUserId);
    }

    public boolean isEitherDirectionBlocked(Long userAId, Long userBId) {
        return userBlockRepository.existsEitherDirection(userAId, userBId);
    }

    public List<Long> getBlockedUserIds(Long userId) {
        validateUserExists(userId);
        return userBlockRepository.findTargetUserIdsByUserId(userId);
    }

    public List<Long> getBlockedUserIdsEitherDirection(Long userId) {
        validateUserExists(userId);
        return getBlockedUserIdsEitherDirectionForExistingUser(userId);
    }

    public List<Long> getBlockedUserIdsEitherDirectionForExistingUser(Long userId) {
        Set<Long> blockedUserIds = new LinkedHashSet<>(
                userBlockRepository.findBlockedUserIdsEitherDirectionByUserId(userId));
        return List.copyOf(blockedUserIds);
    }

    private void validateUserExists(Long userId) {
        userReadableResolver.ensureExists(userId);
    }
}
