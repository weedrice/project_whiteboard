package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.user.dto.BlockedUserResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserBlock;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
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

    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final UserWritableResolver userWritableResolver;

    @Transactional
    public void blockUser(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.CANNOT_BLOCK_SELF);
        }

        User user = userWritableResolver.resolve(userId);

        User target = userRepository.findByUserIdAndStatusAndDeletedAtIsNull(targetUserId, User.STATUS_ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (userBlockRepository.existsByUserAndTarget(user, target)) {
            throw new BusinessException(ErrorCode.ALREADY_BLOCKED);
        }

        UserBlock userBlock = UserBlock.builder()
                .user(user)
                .target(target)
                .build();

        try {
            userBlockRepository.saveAndFlush(userBlock);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.ALREADY_BLOCKED);
        }
    }

    @Transactional
    public void unblockUser(Long userId, Long targetUserId) {
        User user = userWritableResolver.resolve(userId);

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        UserBlock userBlock = userBlockRepository.findByUserAndTarget(user, target)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        userBlockRepository.delete(userBlock);
    }

    public Page<BlockedUserResponse> getBlockedUsers(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Pageable safePageable = normalizeBlockPageable(pageable);
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

    private Pageable normalizeBlockPageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequestUtils.of(0, DEFAULT_BLOCK_PAGE_SIZE);
        }
        return PageRequestUtils.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    private void validateUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }
}
