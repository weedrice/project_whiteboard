package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.user.entity.DisplayNameHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.DisplayNameHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final DisplayNameHistoryRepository displayNameHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    public Long findUserIdByLoginId(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return user.getUserId();
    }

    public User getMyInfo(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    public UserProfileDto getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // TODO: Get post and comment counts from repositories when they are implemented
        long postCount = 0;
        long commentCount = 0;

        return UserProfileDto.builder()
                .userId(user.getUserId())
                .loginId(user.getLoginId())
                .displayName(user.getDisplayName())
                .profileImageUrl(user.getProfileImageUrl())
                .createdAt(user.getCreatedAt())
                .postCount(postCount)
                .commentCount(commentCount)
                .build();
    }

    @Transactional
    public User updateMyProfile(Long userId, String displayName, String profileImageUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String oldDisplayName = user.getDisplayName();

        // Update display name if provided and different
        if (displayName != null && !displayName.equals(oldDisplayName)) {
            // Save display name history
            DisplayNameHistory history = DisplayNameHistory.builder()
                    .user(user)
                    .previousName(oldDisplayName)
                    .newName(displayName)
                    .build();
            displayNameHistoryRepository.save(history);

            user.updateDisplayName(displayName);
        }

        // Update profile image if provided
        if (profileImageUrl != null) {
            user.updateProfileImage(profileImageUrl);
        }

        return user;
    }

    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Verify password
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        // Soft delete
        user.delete();
    }

    @lombok.Builder
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class UserProfileDto {
        private Long userId;
        private String loginId;
        private String displayName;
        private String profileImageUrl;
        private LocalDateTime createdAt;
        private long postCount;
        private long commentCount;
    }
}