package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.PasswordHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    @DisplayName("내 정보 조회 성공")
    void getMyInfo_success() {
        // given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        User foundUser = userService.getMyInfo(userId);

        // then
        assertThat(foundUser).isEqualTo(user);
    }

    @Test
    @DisplayName("프로필 수정 성공 - 닉네임 변경")
    void updateMyProfile_success_displayName() {
        // given
        Long userId = 1L;
        String newDisplayName = "New Name";
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        User updatedUser = userService.updateMyProfile(userId, newDisplayName, null, null);

        // then
        assertThat(updatedUser.getDisplayName()).isEqualTo(newDisplayName);
        verify(displayNameHistoryRepository).save(any());
    }

    @Test
    @DisplayName("프로필 수정 성공 - 프로필 이미지 변경")
    void updateMyProfile_success_profileImage() {
        // given
        Long userId = 1L;
        String newProfileImageUrl = "https://new-image.com/img.png";
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        User updatedUser = userService.updateMyProfile(userId,null, newProfileImageUrl, null);

        // then
        assertThat(updatedUser.getProfileImageUrl()).isEqualTo(newProfileImageUrl);
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
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userService.updatePassword(userId, currentPassword, newPassword);
        });
        assertThat(exception.getErrorCode().getCode()).isEqualTo("U005");
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
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userService.deleteAccount(userId, password);
        });
        assertThat(exception.getErrorCode().getCode()).isEqualTo("U004");
    }
}
