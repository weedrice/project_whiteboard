package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.dto.MyInfoResponse;
import com.weedrice.whiteboard.domain.user.dto.UpdateProfileResponse;
import com.weedrice.whiteboard.domain.user.dto.UserProfileResponse;
import com.weedrice.whiteboard.domain.user.entity.DisplayNameHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.DisplayNameHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private static final int MAX_PASSWORD_LENGTH = 20;

    private static final int DISPLAY_NAME_MIN_LENGTH = 2;
    private static final int DISPLAY_NAME_MAX_LENGTH = 50;
    private static final String PROFILE_IMAGE_CHANGE_COST_CONFIG_KEY = "POINT_PROFILE_IMAGE_CHANGE_COST";
    private static final int DEFAULT_PROFILE_IMAGE_CHANGE_COST = 1000;
    private static final String PROFILE_IMAGE_CHANGE_DESCRIPTION = "프로필 이미지 변경";
    private static final String PROFILE_IMAGE_RELATED_TYPE = "PROFILE_IMAGE";
    private static final String BLOCKED_PUBLIC_PROFILE_DISPLAY_NAME = "차단된 사용자";

    private final UserRepository userRepository;
    private final CurrentUserSummaryAssembler currentUserSummaryAssembler;
    private final CommentRepository commentRepository;
    private final DisplayNameHistoryRepository displayNameHistoryRepository;
    private final PostRepository postRepository;
    private final UserBlockRepository userBlockRepository;
    private final FileService fileService;
    private final PointService pointService;
    private final GlobalConfigService globalConfigService;
    private final PasswordEncoder passwordEncoder;
    private final UserReadableResolver userReadableResolver;
    private final UserWritableResolver userWritableResolver;
    private final UserLifecycleService userLifecycleService;
    private final Clock clock;
    private final MessageSource messageSource;

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
                .profileImageChangeCost(resolveProfileImageChangeCost())
                .profileImageChangeFreeAvailable(user.canUseFreeProfileImageChange())
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
                .displayName(resolveMessage("user.profile.blocked", null, BLOCKED_PUBLIC_PROFILE_DISPLAY_NAME))
                .profileImageUrl(null)
                .createdAt(null)
                .postCount(0)
                .commentCount(0)
                .build();
    }

    @Transactional
    public UpdateProfileResponse updateMyProfile(Long userId, String displayName, Long profileImageId) {
        return updateMyProfile(userId, displayName, profileImageId, false);
    }

    @Transactional
    public UpdateProfileResponse updateMyProfile(
            Long userId,
            String displayName,
            Long profileImageId,
            Boolean removeProfileImage) {
        validateProfileImageRequest(profileImageId, removeProfileImage);
        String normalizedDisplayName = normalizeDisplayName(displayName);
        boolean imageMutationRequested = profileImageId != null || Boolean.TRUE.equals(removeProfileImage);
        boolean profileMutationRequested = normalizedDisplayName != null || imageMutationRequested;
        User user = profileMutationRequested
                ? userWritableResolver.resolveForUpdate(userId)
                : userWritableResolver.resolve(userId);
        String oldDisplayName = user.getDisplayName();
        Integer spentPoints = null;
        Integer remainingPoints = null;

        if (normalizedDisplayName != null && !normalizedDisplayName.equals(oldDisplayName)) {
            displayNameHistoryRepository.save(DisplayNameHistory.builder()
                    .user(user)
                    .previousName(oldDisplayName)
                    .newName(normalizedDisplayName)
                    .changedAt(LocalDateTime.now(clock))
                    .build());
            user.updateDisplayName(normalizedDisplayName);
        }

        if (Boolean.TRUE.equals(removeProfileImage)) {
            removeProfileImage(user);
        } else if (profileImageId != null && !isCurrentProfileImage(user, profileImageId)) {
            ProfileImageChargeResult chargeResult = chargeProfileImageChangeIfNeeded(user, profileImageId);
            spentPoints = chargeResult.spentPoints();
            remainingPoints = chargeResult.remainingPoints();
            user.updateProfileImage(fileService.replaceUserProfileImageForLockedUser(profileImageId, userId, user));
        }

        return new UpdateProfileResponse(
                user.getUserId(),
                user.getDisplayName(),
                user.getProfileImageUrl(),
                spentPoints,
                remainingPoints);
    }

    private void validateProfileImageRequest(Long profileImageId, Boolean removeProfileImage) {
        if (profileImageId != null && Boolean.TRUE.equals(removeProfileImage)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private ProfileImageChargeResult chargeProfileImageChangeIfNeeded(User user, Long profileImageId) {
        if (user.canUseFreeProfileImageChange()) {
            user.markProfileImageChangeFreeUsed();
            return new ProfileImageChargeResult(null, null);
        }

        int cost = resolveProfileImageChangeCost();
        pointService.spendPointForPrevalidatedUser(
                user,
                cost,
                resolveMessage(
                        "point.description.profile-image-change",
                        null,
                        PROFILE_IMAGE_CHANGE_DESCRIPTION),
                profileImageId,
                PROFILE_IMAGE_RELATED_TYPE);
        return new ProfileImageChargeResult(cost, pointService.getCurrentBalance(user.getUserId()));
    }

    private boolean isCurrentProfileImage(User user, Long profileImageId) {
        return profileImageId.equals(FileService.extractFileIdFromUrl(user.getProfileImageUrl()));
    }

    private void removeProfileImage(User user) {
        Long currentProfileImageId = FileService.extractFileIdFromUrl(user.getProfileImageUrl());
        user.updateProfileImage(null);
        if (currentProfileImageId != null) {
            fileService.deleteFileWithStorageIfAssociated(
                    currentProfileImageId,
                    user.getUserId(),
                    FileService.RELATED_TYPE_USER_PROFILE);
        }
    }

    private int resolveProfileImageChangeCost() {
        return GlobalConfigService.parseIntConfigOrDefault(
                globalConfigService.getConfig(PROFILE_IMAGE_CHANGE_COST_CONFIG_KEY),
                DEFAULT_PROFILE_IMAGE_CHANGE_COST,
                0);
    }

    private String resolveMessage(String key, Object[] arguments, String fallback) {
        return messageSource.getMessage(key, arguments, fallback, LocaleContextHolder.getLocale());
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
        validateCurrentPasswordInput(password);
        User user = userWritableResolver.resolveForUpdate(userId);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        removeProfileImage(user);
        userLifecycleService.deletePrelockedAccount(user);
    }

    private void validateCurrentPasswordInput(String password) {
        if (password == null || password.isBlank() || password.length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private record ProfileImageChargeResult(Integer spentPoints, Integer remainingPoints) {
    }
}
