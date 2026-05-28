package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.dto.MyInfoResponse;
import com.weedrice.whiteboard.domain.user.dto.UpdateProfileResponse;
import com.weedrice.whiteboard.domain.user.dto.UserProfileResponse;
import com.weedrice.whiteboard.domain.user.entity.DisplayNameHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.DisplayNameHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
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

    private static final int DISPLAY_NAME_MIN_LENGTH = 2;
    private static final int DISPLAY_NAME_MAX_LENGTH = 50;
    private static final String BLOCKED_PUBLIC_PROFILE_DISPLAY_NAME = "차단된 사용자";

    private final UserRepository userRepository;
    private final CurrentUserSummaryAssembler currentUserSummaryAssembler;
    private final CommentRepository commentRepository;
    private final DisplayNameHistoryRepository displayNameHistoryRepository;
    private final PostRepository postRepository;
    private final UserBlockRepository userBlockRepository;
    private final FileService fileService;
    private final PasswordEncoder passwordEncoder;
    private final UserReadableResolver userReadableResolver;
    private final UserWritableResolver userWritableResolver;
    private final UserLifecycleService userLifecycleService;

    public Long findUserIdByLoginId(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return user.getUserId();
    }

    public MyInfoResponse getMyInfo(Long userId) {
        User user = userReadableResolver.resolve(userId);
        CurrentUserSummaryAssembler.CurrentUserSummary userSummary = currentUserSummaryAssembler.assemble(user);

        return MyInfoResponse.builder()
                .userId(userSummary.userId())
                .loginId(userSummary.loginId())
                .email(userSummary.email())
                .displayName(userSummary.displayName())
                .profileImageUrl(userSummary.profileImageUrl())
                .status(userSummary.status())
                .role(userSummary.role())
                .theme(userSummary.theme())
                .isEmailVerified(userSummary.isEmailVerified())
                .createdAt(userSummary.createdAt())
                .lastLoginAt(userSummary.lastLoginAt())
                .points(userSummary.points())
                .build();
    }

    public UserProfileResponse getUserProfile(Long userId) {
        return getUserProfile(userId, null);
    }

    public UserProfileResponse getUserProfile(Long userId, Long viewerUserId) {
        User user = userReadableResolver.resolveActive(userId);

        if (isRestrictedProfile(user.getUserId(), viewerUserId)) {
            return restrictedProfile(user.getUserId());
        }

        long postCount = postRepository.countPublicProfilePostsByUser(user);
        long commentCount = commentRepository.countPublicProfileCommentsByUser(user);

        return UserProfileResponse.builder()
                .userId(user.getUserId())
                .displayName(user.getDisplayName())
                .profileImageUrl(user.getProfileImageUrl())
                .createdAt(user.getCreatedAt())
                .postCount(postCount)
                .commentCount(commentCount)
                .build();
    }

    private boolean isRestrictedProfile(Long targetUserId, Long viewerUserId) {
        return viewerUserId != null
                && !viewerUserId.equals(targetUserId)
                && userBlockRepository.existsEitherDirection(viewerUserId, targetUserId);
    }

    private UserProfileResponse restrictedProfile(Long targetUserId) {
        return UserProfileResponse.builder()
                .userId(targetUserId)
                .displayName(BLOCKED_PUBLIC_PROFILE_DISPLAY_NAME)
                .profileImageUrl(null)
                .createdAt(null)
                .postCount(0)
                .commentCount(0)
                .build();
    }

    @Transactional
    public UpdateProfileResponse updateMyProfile(Long userId, String displayName, Long profileImageId) {
        User user = profileImageId == null
                ? userWritableResolver.resolve(userId)
                : userWritableResolver.resolveForUpdate(userId);
        String oldDisplayName = user.getDisplayName();
        String normalizedDisplayName = normalizeDisplayName(displayName);

        if (normalizedDisplayName != null && !normalizedDisplayName.equals(oldDisplayName)) {
            displayNameHistoryRepository.save(DisplayNameHistory.builder()
                    .user(user)
                    .previousName(oldDisplayName)
                    .newName(normalizedDisplayName)
                    .build());
            user.updateDisplayName(normalizedDisplayName);
        }

        if (profileImageId != null) {
            user.updateProfileImage(fileService.replaceUserProfileImageForLockedUser(
                    profileImageId,
                    userId,
                    user));
        }

        return new UpdateProfileResponse(user.getUserId(), user.getDisplayName(), user.getProfileImageUrl());
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }

        String normalizedDisplayName = displayName.strip();
        if (normalizedDisplayName.isBlank()
                || normalizedDisplayName.length() < DISPLAY_NAME_MIN_LENGTH
                || normalizedDisplayName.length() > DISPLAY_NAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedDisplayName;
    }

    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = userWritableResolver.resolve(userId);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        userLifecycleService.deleteAccount(user.getUserId());
    }

}
