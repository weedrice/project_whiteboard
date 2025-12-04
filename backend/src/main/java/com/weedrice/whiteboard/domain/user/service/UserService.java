package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository; // Import PostRepository
import com.weedrice.whiteboard.domain.user.entity.DisplayNameHistory;
import com.weedrice.whiteboard.domain.user.entity.PasswordHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.DisplayNameHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.PasswordHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final DisplayNameHistoryRepository displayNameHistoryRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserSettingsRepository userSettingsRepository;
    private final PostRepository postRepository; // Inject PostRepository

    public Long findUserIdByLoginId(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return user.getUserId();
    }

    public User getMyInfo(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    public UserService.UserProfileDto getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        long postCount = postRepository.countByUserAndIsDeleted(user, "N"); // Calculate postCount
        long commentCount = commentRepository.countByUser(user); // 댓글 수 계산

        return UserProfileDto.builder()
                .userId(user.getUserId())
                .loginId(user.getLoginId())
                .displayName(user.getDisplayName())
                .profileImageUrl(user.getProfileImageUrl())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .postCount(postCount)
                .commentCount(commentCount)
                .build();
    }

    @Transactional
    public User updateMyProfile(Long userId, String displayName, String profileImageUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String oldDisplayName = user.getDisplayName();

        if (displayName != null && !displayName.equals(oldDisplayName)) {
            DisplayNameHistory history = DisplayNameHistory.builder()
                    .user(user)
                    .previousName(oldDisplayName)
                    .newName(displayName)
                    .build();
            displayNameHistoryRepository.save(history);

            user.updateDisplayName(displayName);
        }

        if (profileImageUrl != null) {
            user.updateProfileImage(profileImageUrl);
        }

        return user;
    }

    @Transactional
    public void updatePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }

        List<PasswordHistory> passwordHistories = passwordHistoryRepository.findTop3ByUserOrderByCreatedAtDesc(user);
        for (PasswordHistory history : passwordHistories) {
            if (passwordEncoder.matches(newPassword, history.getPasswordHash())) {
                throw new BusinessException(ErrorCode.PASSWORD_RECENTLY_USED);
            }
        }

        String newPasswordHash = passwordEncoder.encode(newPassword);
        user.updatePassword(newPasswordHash);

        PasswordHistory history = PasswordHistory.builder()
                .user(user)
                .passwordHash(newPasswordHash)
                .build();
        passwordHistoryRepository.save(history);
    }

    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        user.delete();
    }

    public Page<Comment> getMyComments(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return commentRepository.findByUserOrderByCreatedAtDesc(user, pageable);
    }

    public Page<User> searchUsers(String keyword, Pageable pageable) {
        return userRepository.searchUsers(keyword, pageable);
    }

    @Transactional
    public void updateUserStatus(Long userId, String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if ("SUSPENDED".equals(status)) {
            user.suspend();
        } else if ("ACTIVE".equals(status)) {
            user.activate();
        } else {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    @Transactional
    public UserSettings updateSettings(Long userId, String theme, String language, String timezone, String hideNsfw) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        UserSettings settings = userSettingsRepository.findById(userId)
                .orElseGet(() -> {
                    UserSettings newSettings = new UserSettings(user);
                    return userSettingsRepository.save(newSettings);
                });

        settings.updateSettings(theme, language, timezone, hideNsfw);
        return settings;
    }

    public UserSettings getSettings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return userSettingsRepository.findById(userId)
                .orElseGet(() -> new UserSettings(user));
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
        private LocalDateTime lastLoginAt;
        private long postCount;
        private long commentCount;
    }
}
