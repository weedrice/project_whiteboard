package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.user.dto.BlockedUserResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserBlock;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserBlockService {

        private final UserRepository userRepository;
        private final UserBlockRepository userBlockRepository;

        @Transactional
        public void blockUser(Long userId, Long targetUserId) {
                if (userId.equals(targetUserId)) {
                        throw new BusinessException(ErrorCode.CANNOT_BLOCK_SELF);
                }

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                User target = userRepository.findById(targetUserId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                if (userBlockRepository.existsByUserAndTarget(user, target)) {
                        throw new BusinessException(ErrorCode.ALREADY_BLOCKED);
                }

                UserBlock userBlock = UserBlock.builder()
                                .user(user)
                                .target(target)
                                .build();

                userBlockRepository.save(userBlock);
        }

        @Transactional
        public void unblockUser(Long userId, Long targetUserId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                User target = userRepository.findById(targetUserId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                UserBlock userBlock = userBlockRepository.findByUserAndTarget(user, target)
                                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

                userBlockRepository.delete(userBlock);
        }

        public Page<BlockedUserResponse> getBlockedUsers(Long userId, Pageable pageable) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                Page<UserBlock> blocks = userBlockRepository.findByUserOrderByCreatedAtDesc(user, pageable);

                return blocks.map(block -> new BlockedUserResponse(
                                block.getTarget().getUserId(),
                                block.getTarget().getLoginId(),
                                block.getTarget().getDisplayName(),
                                block.getCreatedAt()));
        }

        public boolean isBlocked(Long userId, Long targetUserId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                User target = userRepository.findById(targetUserId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                return userBlockRepository.existsByUserAndTarget(user, target);
        }

        public List<Long> getBlockedUserIds(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                return userBlockRepository.findByUser(user).stream()
                                .map(block -> block.getTarget().getUserId())
                                .collect(Collectors.toList());
        }
}
