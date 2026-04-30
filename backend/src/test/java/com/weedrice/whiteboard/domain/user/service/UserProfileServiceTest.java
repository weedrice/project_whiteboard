package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.agent.service.AgentLifecycleService;
import com.weedrice.whiteboard.domain.auth.service.RefreshTokenLifecycleService;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.point.entity.UserPoint;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.dto.MyInfoResponse;
import com.weedrice.whiteboard.domain.user.dto.UpdateProfileResponse;
import com.weedrice.whiteboard.domain.user.dto.UserProfileResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.DisplayNameHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private UserProfileService userProfileService;

    @Mock private UserRepository userRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private DisplayNameHistoryRepository displayNameHistoryRepository;
    @Mock private PostRepository postRepository;
    @Mock private FileService fileService;
    @Mock private UserPointRepository userPointRepository;
    @Mock private SanctionService sanctionService;
    @Mock private AgentLifecycleService agentLifecycleService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenLifecycleService refreshTokenLifecycleService;
    @Mock private UserPrivilegeCleanupService userPrivilegeCleanupService;

    @BeforeEach
    void setUp() {
        UserWritableResolver userWritableResolver = new UserWritableResolver(userRepository, sanctionService);
        userProfileService = new UserProfileService(
                userRepository,
                userSettingsRepository,
                commentRepository,
                displayNameHistoryRepository,
                postRepository,
                fileService,
                userPointRepository,
                agentLifecycleService,
                passwordEncoder,
                refreshTokenLifecycleService,
                userPrivilegeCleanupService,
                userWritableResolver);
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
    }

    @Test
    @DisplayName("프로필 이미지 변경 시 파일 서비스 결과를 반영한다")
    void updateMyProfile_imageChange() {
        User user = User.builder().displayName("Name").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fileService.replaceUserProfileImage(100L, 1L, 1L)).thenReturn("/api/v1/files/100");

        UpdateProfileResponse response = userProfileService.updateMyProfile(1L, null, 100L);

        assertThat(response.getProfileImageUrl()).isEqualTo("/api/v1/files/100");
        verify(fileService).replaceUserProfileImage(100L, 1L, 1L);
    }

    @Test
    @DisplayName("회원 탈퇴 성공")
    void deleteAccount_success() {
        User user = User.builder().password("encodedPass").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass", "encodedPass")).thenReturn(true);
        doAnswer(invocation -> {
            assertThat(user.getStatus()).isEqualTo("ACTIVE");
            return null;
        }).when(agentLifecycleService).suspendAllForUser(user);

        userProfileService.deleteAccount(1L, "pass");

        assertThat(user.getStatus()).isEqualTo("DELETED");
        var inOrder = inOrder(refreshTokenLifecycleService, userPrivilegeCleanupService, agentLifecycleService);
        inOrder.verify(refreshTokenLifecycleService).revokeActiveRefreshTokens(user);
        inOrder.verify(userPrivilegeCleanupService).removeOperationalPrivileges(user);
        inOrder.verify(agentLifecycleService).suspendAllForUser(user);
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

        verify(refreshTokenLifecycleService, never()).revokeActiveRefreshTokens(any());
        verify(userPrivilegeCleanupService, never()).removeOperationalPrivileges(any());
        verify(agentLifecycleService, never()).suspendAllForUser(any());
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
}
