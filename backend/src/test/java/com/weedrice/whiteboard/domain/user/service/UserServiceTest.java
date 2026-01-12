package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.dto.MyInfoResponse;
import com.weedrice.whiteboard.domain.user.dto.UpdateProfileResponse;
import com.weedrice.whiteboard.domain.user.dto.UserProfileResponse;
import com.weedrice.whiteboard.domain.user.entity.PasswordHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.DisplayNameHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.PasswordHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private DisplayNameHistoryRepository displayNameHistoryRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PasswordHistoryRepository passwordHistoryRepository;
    @Mock
    private UserSettingsRepository userSettingsRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private FileService fileService;
    @Mock
    private UserPointRepository userPointRepository;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("testuser")
                .password("encodedPassword")
                .email("test@example.com")
                .displayName("Test User")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
    }

    @Test
    @DisplayName("로그인 ID로 사용자 ID 찾기 성공")
    void findUserIdByLoginId_success() {
        // given
        String loginId = "testuser";
        when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));

        // when
        Long foundUserId = userService.findUserIdByLoginId(loginId);

        // then
        assertThat(foundUserId).isEqualTo(1L);
    }

    @Test
    @DisplayName("로그인 ID로 사용자 ID 찾기 실패 - 사용자를 찾을 수 없음")
    void findUserIdByLoginId_fail_userNotFound() {
        // given
        String loginId = "nonexistentuser";
        when(userRepository.findByLoginId(loginId)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userService.findUserIdByLoginId(loginId);
        });
        assertThat(exception.getErrorCode().getCode()).isEqualTo("U001");
    }

    @Test
    @DisplayName("내 정보 조회 성공")
    void getMyInfo_success() {
        // given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userPointRepository.findById(userId)).thenReturn(Optional.empty());

        // when
        MyInfoResponse myInfoResponse = userService.getMyInfo(userId);

        // then
        assertThat(myInfoResponse.getUserId()).isEqualTo(userId);
        assertThat(myInfoResponse.getLoginId()).isEqualTo(user.getLoginId());
    }

    @Test
    @DisplayName("사용자 프로필 조회 성공")
    void getUserProfile_success() {
        // given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(postRepository.countByUserAndIsDeleted(user, false)).thenReturn(10L);
        when(commentRepository.countByUser(user)).thenReturn(20L);

        // when
        UserProfileResponse userProfile = userService.getUserProfile(userId);

        // then
        assertThat(userProfile.getUserId()).isEqualTo(userId);
        assertThat(userProfile.getLoginId()).isEqualTo(user.getLoginId());
        assertThat(userProfile.getPostCount()).isEqualTo(10L);
        assertThat(userProfile.getCommentCount()).isEqualTo(20L);
    }

    @Test
    @DisplayName("프로필 수정 성공 - 닉네임 변경")
    void updateMyProfile_success_displayName() {
        // given
        Long userId = 1L;
        String newDisplayName = "New Name";
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        UpdateProfileResponse updateProfileResponse = userService.updateMyProfile(userId, newDisplayName, null, null);

        // then
        assertThat(updateProfileResponse.getDisplayName()).isEqualTo(newDisplayName);
        verify(displayNameHistoryRepository).save(any());
    }

    @Test
    @DisplayName("프로필 수정 - 닉네임 변경 없음")
    void updateMyProfile_doesNotUpdateDisplayNameIfUnchanged() {
        // given
        Long userId = 1L;
        String sameDisplayName = user.getDisplayName();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        userService.updateMyProfile(userId, sameDisplayName, null, null);

        // then
        verify(displayNameHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("프로필 수정 성공 - 프로필 이미지 URL 변경")
    void updateMyProfile_success_profileImageUrl() {
        // given
        Long userId = 1L;
        String newProfileImageUrl = "https://new-image.com/img.png";
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        UpdateProfileResponse updateProfileResponse = userService.updateMyProfile(userId, null, newProfileImageUrl, null);

        // then
        assertThat(updateProfileResponse.getProfileImageUrl()).isEqualTo(newProfileImageUrl);
    }

    @Test
    @DisplayName("프로필 수정 성공 - 프로필 이미지 ID로 연결")
    void updateMyProfile_success_profileImageId() {
        // given
        Long userId = 1L;
        Long profileImageId = 123L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        userService.updateMyProfile(userId, null, null, profileImageId);

        // then
        verify(fileService).associateFileWithEntity(profileImageId, userId, "USER_PROFILE");
    }

    @Test
    @DisplayName("비밀번호 변경 성공")
    void updatePassword_success() {
        // given
        Long userId = 1L;
        String currentPassword = "currentPassword";
        String newPassword = "newPassword";
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(currentPassword, user.getPassword())).thenReturn(true);
        when(passwordHistoryRepository.findTop3ByUserOrderByCreatedAtDesc(user)).thenReturn(Collections.emptyList());
        when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");

        // when
        userService.updatePassword(userId, currentPassword, newPassword);

        // then
        assertThat(user.getPassword()).isEqualTo("encodedNewPassword");
        verify(passwordHistoryRepository).save(any());
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 현재 비밀번호 불일치")
    void updatePassword_fail_wrong_password() {
        // given
        Long userId = 1L;
        String currentPassword = "wrongPassword";
        String newPassword = "newPassword";
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(currentPassword, user.getPassword())).thenReturn(false);

        // when & then
        assertThrows(BusinessException.class, () -> {
            userService.updatePassword(userId, currentPassword, newPassword);
        });
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 최근 사용한 비밀번호")
    void updatePassword_fail_passwordRecentlyUsed() {
        // given
        Long userId = 1L;
        String currentPassword = "currentPassword";
        String newPassword = "newPassword";
        PasswordHistory passwordHistory = PasswordHistory.builder().user(user).passwordHash("encodedNewPassword").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(currentPassword, user.getPassword())).thenReturn(true);
        when(passwordHistoryRepository.findTop3ByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(passwordHistory));
        when(passwordEncoder.matches(newPassword, "encodedNewPassword")).thenReturn(true);

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userService.updatePassword(userId, currentPassword, newPassword);
        });
        assertThat(exception.getErrorCode().getCode()).isEqualTo("U006");
    }

    @Test
    @DisplayName("회원 탈퇴 성공")
    void deleteAccount_success() {
        // given
        Long userId = 1L;
        String password = "password";
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, user.getPassword())).thenReturn(true);

        // when
        userService.deleteAccount(userId, password);

        // then
        assertThat(user.getStatus()).isEqualTo("DELETED");
    }

    @Test
    @DisplayName("회원 탈퇴 실패 - 비밀번호 불일치")
    void deleteAccount_fail_wrong_password() {
        // given
        Long userId = 1L;
        String password = "wrongPassword";
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, user.getPassword())).thenReturn(false);

        // when & then
        assertThrows(BusinessException.class, () -> {
            userService.deleteAccount(userId, password);
        });
    }

    @Test
    @DisplayName("내 댓글 목록 조회 성공")
    void getMyComments_success() {
        // given
        Long userId = 1L;
        Pageable pageable = Pageable.unpaged();
        Comment comment = Comment.builder().user(user).content("My Comment").build();
        Page<Comment> page = new PageImpl<>(Collections.singletonList(comment));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(commentRepository.findByUserOrderByCreatedAtDesc(user, pageable)).thenReturn(page);

        // when
        Page<Comment> result = userService.getMyComments(userId, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getContent()).isEqualTo("My Comment");
    }

    @Test
    @DisplayName("사용자 검색 성공")
    void searchUsers_success() {
        // given
        String keyword = "test";
        Pageable pageable = Pageable.unpaged();
        Page<User> page = new PageImpl<>(Collections.singletonList(user));

        when(userRepository.searchUsers(keyword, pageable)).thenReturn(page);

        // when
        Page<User> result = userService.searchUsers(keyword, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getLoginId()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("사용자 상태 변경 성공 - 정지")
    void updateUserStatus_success_suspend() {
        // given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        userService.updateUserStatus(userId, "SUSPENDED");

        // then
        assertThat(user.getStatus()).isEqualTo("SUSPENDED");
    }

    @Test
    @DisplayName("사용자 상태 변경 성공 - 활성")
    void updateUserStatus_success_activate() {
        // given
        Long userId = 1L;
        user.suspend(); // Start from a non-active state
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        userService.updateUserStatus(userId, "ACTIVE");

        // then
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("사용자 상태 변경 실패 - 유효하지 않은 상태")
    void updateUserStatus_fail_invalidStatus() {
        // given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userService.updateUserStatus(userId, "INVALID_STATUS");
        });
        assertThat(exception.getErrorCode().getCode()).isEqualTo("C001"); // INVALID_INPUT_VALUE
    }

    @Test
    @DisplayName("설정 수정 성공 - 기존 설정 존재")
    void updateSettings_success_existingSettings() {
        // given
        Long userId = 1L;
        UserSettings settings = new UserSettings(user);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(userId)).thenReturn(Optional.of(settings));

        // when
        UserSettings updatedSettings = userService.updateSettings(userId, "dark", "en", "UTC", true);

        // then
        assertThat(updatedSettings.getTheme()).isEqualTo("dark");
        assertThat(updatedSettings.getLanguage()).isEqualTo("en");
        assertThat(updatedSettings.getTimezone()).isEqualTo("UTC");
        assertThat(updatedSettings.getHideNsfw()).isTrue();
    }

    @Test
    @DisplayName("설정 수정 성공 - 새로운 설정 생성")
    void updateSettings_success_newSettings() {
        // given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(userId)).thenReturn(Optional.empty());
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UserSettings updatedSettings = userService.updateSettings(userId, "dark", "en", "UTC", true);

        // then
        assertThat(updatedSettings.getTheme()).isEqualTo("dark");
        verify(userSettingsRepository).save(any(UserSettings.class));
    }

    @Test
    @DisplayName("설정 조회 성공 - 기존 설정 존재")
    void getSettings_success_existingSettings() {
        // given
        Long userId = 1L;
        UserSettings settings = new UserSettings(user);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(userId)).thenReturn(Optional.of(settings));

        // when
        UserSettings foundSettings = userService.getSettings(userId);

        // then
        assertThat(foundSettings).isEqualTo(settings);
        verify(userSettingsRepository, never()).save(any());
    }



    @Test
    @DisplayName("설정 조회 성공 - 새로운 설정 생성")
    void getSettings_success_newSettings() {
        // given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(userId)).thenReturn(Optional.empty());

        // when
        UserSettings foundSettings = userService.getSettings(userId);

        // then
        assertThat(foundSettings.getUser()).isEqualTo(user);
        // Default values can be checked if they are defined in the entity
        assertThat(foundSettings.getTheme()).isEqualTo("LIGHT"); // Assuming "light" is default
    }

    @Test
    @DisplayName("사용자 검색 - 빈 결과")
    void searchUsers_empty() {
        // given
        String keyword = "nonexistent";
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(userRepository.findByDisplayNameContainingIgnoreCase(keyword, pageable)).thenReturn(emptyPage);

        // when
        Page<User> result = userService.searchUsers(keyword, pageable);

        // then
        assertThat(result.getContent()).isEmpty();
        verify(userRepository).findByDisplayNameContainingIgnoreCase(keyword, pageable);
    }

    @Test
    @DisplayName("내 댓글 목록 조회 - 빈 결과")
    void getMyComments_empty() {
        // given
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        Page<Comment> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(commentRepository.findByUserAndIsDeletedOrderByCreatedAtDesc(user, false, pageable)).thenReturn(emptyPage);

        // when
        Page<Comment> result = userService.getMyComments(userId, pageable);

        // then
        assertThat(result.getContent()).isEmpty();
        verify(commentRepository).findByUserAndIsDeletedOrderByCreatedAtDesc(user, false, pageable);
    }
}
