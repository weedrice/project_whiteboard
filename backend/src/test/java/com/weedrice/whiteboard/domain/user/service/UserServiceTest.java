package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.DisplayNameHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
        User updatedUser = userService.updateMyProfile(userId, newDisplayName, null);

        // then
        assertThat(updatedUser.getDisplayName()).isEqualTo(newDisplayName);
        verify(displayNameHistoryRepository).save(any());
    }

    @Test
    @DisplayName("프로필 수정 성공 - 프로필 이미지 변경")
    void updateMyProfile_success_profileImage() {
        // given
        Long userId = 1L;
        String newProfileImageUrl = "http://new-image.com/img.png";
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        User updatedUser = userService.updateMyProfile(userId, null, newProfileImageUrl);

        // then
        assertThat(updatedUser.getProfileImageUrl()).isEqualTo(newProfileImageUrl);
    }
}
