package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.point.entity.UserPoint;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.dto.MyInfoResponse;
import com.weedrice.whiteboard.domain.user.dto.UpdateProfileResponse;
import com.weedrice.whiteboard.domain.user.dto.UserProfileResponse;
import com.weedrice.whiteboard.domain.user.entity.DisplayNameHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.DisplayNameHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private UserProfileService userProfileService;

    @Mock private UserRepository userRepository;
    @Mock private UserBlockRepository userBlockRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private DisplayNameHistoryRepository displayNameHistoryRepository;
    @Mock private PostRepository postRepository;
    @Mock private FileService fileService;
    @Mock private UserPointRepository userPointRepository;
    @Mock private SanctionService sanctionService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserLifecycleService userLifecycleService;

    @BeforeEach
    void setUp() {
        UserWritableResolver userWritableResolver = new UserWritableResolver(userRepository, sanctionService);
        UserReadableResolver userReadableResolver = new UserReadableResolver(userRepository);
        CurrentUserSummaryAssembler currentUserSummaryAssembler =
                new CurrentUserSummaryAssembler(userPointRepository, userSettingsRepository);
        userProfileService = new UserProfileService(
                userRepository,
                currentUserSummaryAssembler,
                commentRepository,
                displayNameHistoryRepository,
                postRepository,
                userBlockRepository,
                fileService,
                passwordEncoder,
                userReadableResolver,
                userWritableResolver,
                userLifecycleService);
    }

    @Test
    @DisplayName("로그인 ID로 사용자 ID 조회 성공")
    void findUserIdByLoginId_success() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(userRepository.findByLoginId("test")).thenReturn(Optional.of(user));

        assertThat(userProfileService.findUserIdByLoginId("test")).isEqualTo(1L);
    }

    @Test
    @DisplayName("내 정보 조회 성공")
    void getMyInfo_success() {
        User user = User.builder().loginId("test").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(user, "isSuperAdmin", false);
        UserPoint userPoint = UserPoint.builder().user(user).build();
        ReflectionTestUtils.setField(userPoint, "currentPoint", 100);
        UserSettings userSettings = new UserSettings(user);
        userSettings.updateSettings("dark", null, null, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userPointRepository.findById(1L)).thenReturn(Optional.of(userPoint));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(userSettings));

        MyInfoResponse response = userProfileService.getMyInfo(1L);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getPoints()).isEqualTo(100);
        assertThat(response.getTheme()).isEqualTo("DARK");
    }

    @Test
    @DisplayName("getMyInfo returns LIGHT when settings do not exist")
    void getMyInfo_defaultsThemeToLight() {
        User user = User.builder().loginId("test").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userPointRepository.findById(1L)).thenReturn(Optional.empty());
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.empty());

        MyInfoResponse response = userProfileService.getMyInfo(1L);

        assertThat(response.getTheme()).isEqualTo("LIGHT");
    }

    @Test
    @DisplayName("getMyInfo returns LIGHT when stored theme is unsupported")
    void getMyInfo_defaultsUnsupportedThemeToLight() {
        User user = User.builder().loginId("test").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        UserSettings userSettings = new UserSettings(user);
        userSettings.updateSettings("SYSTEM", null, null, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userPointRepository.findById(1L)).thenReturn(Optional.empty());
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(userSettings));

        MyInfoResponse response = userProfileService.getMyInfo(1L);

        assertThat(response.getTheme()).isEqualTo("LIGHT");
    }

    @Test
    @DisplayName("공개 프로필은 활성 사용자와 공개 범위 활동만 집계한다")
    void getUserProfile_success() {
        User user = User.builder().loginId("test").displayName("tester").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(1L, "ACTIVE")).thenReturn(Optional.of(user));
        when(postRepository.countPublicProfilePostsByUser(user)).thenReturn(5L);
        when(commentRepository.countPublicProfileCommentsByUser(user)).thenReturn(7L);

        UserProfileResponse response = userProfileService.getUserProfile(1L);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getDisplayName()).isEqualTo("tester");
        assertThat(response.getPostCount()).isEqualTo(5L);
        assertThat(response.getCommentCount()).isEqualTo(7L);
        verify(postRepository).countPublicProfilePostsByUser(user);
        verify(commentRepository).countPublicProfileCommentsByUser(user);
        verify(userBlockRepository, never()).existsEitherDirection(any(), any());
        verify(postRepository, never()).countByUserAndIsDeleted(user, false);
        verify(commentRepository, never()).countByUserAndIsDeleted(user, false);
    }

    @Test
    @DisplayName("비활성 또는 삭제된 사용자는 공개 프로필에서 제외한다")
    void getUserProfile_userNotFound() {
        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(1L, "ACTIVE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.getUserProfile(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        verify(userBlockRepository, never()).existsEitherDirection(any(), any());
    }

    @Test
    @DisplayName("프로필 이미지 변경 시 파일 서비스 결과를 반영한다")
    void updateMyProfile_imageChange() {
        User user = User.builder().displayName("Name").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(fileService.replaceUserProfileImageForLockedUser(100L, 1L, user)).thenReturn("/api/v1/files/100");

        UpdateProfileResponse response = userProfileService.updateMyProfile(1L, null, 100L);

        assertThat(response.getProfileImageUrl()).isEqualTo("/api/v1/files/100");
        verify(fileService).replaceUserProfileImageForLockedUser(100L, 1L, user);
        verify(userRepository, never()).findById(1L);
    }

    @Test
    @DisplayName("표시명 변경 시 앞뒤 공백을 제거한 최종 값을 저장하고 이력에 남긴다")
    void updateMyProfile_normalizesDisplayName() {
        User user = User.builder().displayName("Old Name").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UpdateProfileResponse response = userProfileService.updateMyProfile(1L, "  New Name  ", null);

        assertThat(response.getDisplayName()).isEqualTo("New Name");
        assertThat(user.getDisplayName()).isEqualTo("New Name");
        ArgumentCaptor<DisplayNameHistory> historyCaptor = ArgumentCaptor.forClass(DisplayNameHistory.class);
        verify(displayNameHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getPreviousName()).isEqualTo("Old Name");
        assertThat(historyCaptor.getValue().getNewName()).isEqualTo("New Name");
        verify(userRepository, never()).findByIdForUpdate(1L);
    }

    @Test
    @DisplayName("정규화한 표시명이 기존 이름과 같으면 변경 이력을 남기지 않는다")
    void updateMyProfile_sameDisplayNameAfterNormalize_doesNotSaveHistory() {
        User user = User.builder().displayName("Same Name").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UpdateProfileResponse response = userProfileService.updateMyProfile(1L, " Same Name ", null);

        assertThat(response.getDisplayName()).isEqualTo("Same Name");
        verify(displayNameHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("공백 표시명은 거부한다")
    void updateMyProfile_blankDisplayName_throwsInvalidInput() {
        User user = User.builder().displayName("Old Name").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userProfileService.updateMyProfile(1L, "   ", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(displayNameHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("정규화한 표시명이 길이 제한을 벗어나면 거부한다")
    void updateMyProfile_normalizedDisplayNameTooShort_throwsInvalidInput() {
        User user = User.builder().displayName("Old Name").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userProfileService.updateMyProfile(1L, " a ", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(displayNameHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("회원 탈퇴 성공")
    void deleteAccount_success() {
        User user = User.builder().password("encodedPass").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass", "encodedPass")).thenReturn(true);

        userProfileService.deleteAccount(1L, "pass");

        verify(userLifecycleService).deleteAccount(1L);
    }

    @Test
    @DisplayName("잘못된 비밀번호면 회원 탈퇴를 거부한다")
    void deleteAccount_wrongPassword() {
        User user = User.builder().password("encodedPass").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encodedPass")).thenReturn(false);

        assertThatThrownBy(() -> userProfileService.deleteAccount(1L, "wrong"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PASSWORD);

        verify(userLifecycleService, never()).deleteAccount(any());
    }

    @Test
    @DisplayName("제재된 사용자는 프로필 수정이 불가능하다")
    void updateMyProfile_bannedUser() {
        User user = User.builder().displayName("Old Name").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE))
                .when(sanctionService)
                .validateNotBanned(user);

        assertThatThrownBy(() -> userProfileService.updateMyProfile(1L, "New Name", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);
    }

    @Test
    @DisplayName("viewer와 공개 프로필 대상이 차단 관계가 아니면 공개 카운트를 반환한다")
    void getUserProfile_authenticatedViewerWithoutBlock_returnsPublicProfile() {
        User user = User.builder().loginId("target").displayName("target").build();
        ReflectionTestUtils.setField(user, "userId", 2L);

        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(2L, "ACTIVE")).thenReturn(Optional.of(user));
        when(userBlockRepository.existsEitherDirection(1L, 2L)).thenReturn(false);
        when(postRepository.countPublicProfilePostsByUser(user)).thenReturn(3L);
        when(commentRepository.countPublicProfileCommentsByUser(user)).thenReturn(4L);

        UserProfileResponse response = userProfileService.getUserProfile(2L, 1L);

        assertThat(response.getUserId()).isEqualTo(2L);
        assertThat(response.getDisplayName()).isEqualTo("target");
        assertThat(response.getPostCount()).isEqualTo(3L);
        assertThat(response.getCommentCount()).isEqualTo(4L);
        verify(userBlockRepository).existsEitherDirection(1L, 2L);
        verify(postRepository).countPublicProfilePostsByUser(user);
        verify(commentRepository).countPublicProfileCommentsByUser(user);
    }

    @Test
    @DisplayName("viewer와 공개 프로필 대상이 차단 관계이면 제한 응답을 반환한다")
    void getUserProfile_blockedRelationship_returnsRestrictedProfile() {
        User user = User.builder().loginId("target").displayName("target").build();
        ReflectionTestUtils.setField(user, "userId", 2L);

        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(2L, "ACTIVE")).thenReturn(Optional.of(user));
        when(userBlockRepository.existsEitherDirection(1L, 2L)).thenReturn(true);

        UserProfileResponse response = userProfileService.getUserProfile(2L, 1L);

        assertThat(response.getUserId()).isEqualTo(2L);
        assertThat(response.getDisplayName()).isEqualTo("차단된 사용자");
        assertThat(response.getProfileImageUrl()).isNull();
        assertThat(response.getCreatedAt()).isNull();
        assertThat(response.getPostCount()).isZero();
        assertThat(response.getCommentCount()).isZero();
        verify(userBlockRepository).existsEitherDirection(1L, 2L);
        verify(postRepository, never()).countPublicProfilePostsByUser(any());
        verify(commentRepository, never()).countPublicProfileCommentsByUser(any());
    }

    @Test
    @DisplayName("자기 프로필 조회는 차단 관계 조회 없이 공개 카운트를 반환한다")
    void getUserProfile_selfViewer_skipsBlockLookup() {
        User user = User.builder().loginId("test").displayName("tester").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(1L, "ACTIVE")).thenReturn(Optional.of(user));
        when(postRepository.countPublicProfilePostsByUser(user)).thenReturn(5L);
        when(commentRepository.countPublicProfileCommentsByUser(user)).thenReturn(7L);

        UserProfileResponse response = userProfileService.getUserProfile(1L, 1L);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getDisplayName()).isEqualTo("tester");
        assertThat(response.getPostCount()).isEqualTo(5L);
        assertThat(response.getCommentCount()).isEqualTo(7L);
        verify(userBlockRepository, never()).existsEitherDirection(any(), any());
    }
}
