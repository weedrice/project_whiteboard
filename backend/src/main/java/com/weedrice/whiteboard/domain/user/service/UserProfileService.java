package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.agent.service.AgentLifecycleService;
import com.weedrice.whiteboard.domain.auth.service.RefreshTokenLifecycleService;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.dto.MyInfoResponse;
import com.weedrice.whiteboard.domain.user.dto.UpdateProfileResponse;
import com.weedrice.whiteboard.domain.user.dto.UserProfileResponse;
import com.weedrice.whiteboard.domain.user.entity.DisplayNameHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.DisplayNameHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private static final String DEFAULT_THEME = "LIGHT";

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final CommentRepository commentRepository;
    private final DisplayNameHistoryRepository displayNameHistoryRepository;
    private final PostRepository postRepository;
    private final FileService fileService;
    private final com.weedrice.whiteboard.domain.point.repository.UserPointRepository userPointRepository;
    private final SanctionService sanctionService;
    private final AgentLifecycleService agentLifecycleService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenLifecycleService refreshTokenLifecycleService;
    private final UserPrivilegeCleanupService userPrivilegeCleanupService;

    public Long findUserIdByLoginId(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return user.getUserId();
    }

    public MyInfoResponse getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String role = Boolean.TRUE.equals(user.getIsSuperAdmin())
                ? com.weedrice.whiteboard.domain.user.entity.Role.SUPER_ADMIN
                : com.weedrice.whiteboard.domain.user.entity.Role.USER;
        Integer points = userPointRepository.findById(user.getUserId())
                .map(com.weedrice.whiteboard.domain.point.entity.UserPoint::getCurrentPoint)
                .orElse(0);

        return MyInfoResponse.builder()
                .userId(user.getUserId())
                .loginId(user.getLoginId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .profileImageUrl(user.getProfileImageUrl())
                .status(user.getStatus())
                .role(role)
                .theme(resolveTheme(user.getUserId()))
                .isEmailVerified(user.getIsEmailVerified())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .points(points)
                .build();
    }

    private String resolveTheme(Long userId) {
        return userSettingsRepository.findById(userId)
                .map(UserSettings::getTheme)
                .map(this::normalizeTheme)
                .orElse(DEFAULT_THEME);
    }

    private String normalizeTheme(String theme) {
        if ("DARK".equalsIgnoreCase(theme)) {
            return "DARK";
        }
        return DEFAULT_THEME;
    }

    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        long postCount = postRepository.countByUserAndIsDeleted(user, false);
        long commentCount = commentRepository.countByUserAndIsDeleted(user, false);

        return UserProfileResponse.builder()
                .userId(user.getUserId())
                .displayName(user.getDisplayName())
                .profileImageUrl(user.getProfileImageUrl())
                .createdAt(user.getCreatedAt())
                .postCount(postCount)
                .commentCount(commentCount)
                .build();
    }

    @Transactional
    public UpdateProfileResponse updateMyProfile(Long userId, String displayName, Long profileImageId) {
        User user = getWritableUser(userId);
        String oldDisplayName = user.getDisplayName();

        if (displayName != null && !displayName.equals(oldDisplayName)) {
            displayNameHistoryRepository.save(DisplayNameHistory.builder()
                    .user(user)
                    .previousName(oldDisplayName)
                    .newName(displayName)
                    .build());
            user.updateDisplayName(displayName);
        }

        if (profileImageId != null) {
            user.updateProfileImage(fileService.replaceUserProfileImage(profileImageId, userId, user.getUserId()));
        }

        return new UpdateProfileResponse(user.getUserId(), user.getDisplayName(), user.getProfileImageUrl());
    }

    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = getWritableUser(userId);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        refreshTokenLifecycleService.revokeActiveRefreshTokens(user);
        userPrivilegeCleanupService.removeOperationalPrivileges(user);
        agentLifecycleService.suspendAllForUser(user);
        user.delete();
    }

    private User getWritableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        sanctionService.validateNotBanned(user);
        return user;
    }
}
