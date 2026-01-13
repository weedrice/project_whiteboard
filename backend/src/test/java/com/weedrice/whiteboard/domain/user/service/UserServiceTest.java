package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.point.entity.UserPoint;
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
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private DisplayNameHistoryRepository displayNameHistoryRepository;
    @Mock
    private PasswordHistoryRepository passwordHistoryRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserSettingsRepository userSettingsRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private FileService fileService;
    @Mock
    private UserPointRepository userPointRepository;

    @Test
    @DisplayName("로그인 ID로 사용자 ID 조회 성공")
    void findUserIdByLoginId_success() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(userRepository.findByLoginId("test")).thenReturn(Optional.of(user));

        Long userId = userService.findUserIdByLoginId("test");
        assertThat(userId).isEqualTo(1L);
    }

    @Test
    @DisplayName("로그인 ID로 사용자 ID 조회 실패 - 사용자 없음")
    void findUserIdByLoginId_notFound() {
        when(userRepository.findByLoginId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findUserIdByLoginId("unknown"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("내 정보 조회 성공")
    void getMyInfo_success() {
        User user = User.builder().loginId("test").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(user, "isSuperAdmin", false);
        
        UserPoint userPoint = UserPoint.builder().user(user).build();
        ReflectionTestUtils.setField(userPoint, "currentPoint", 100);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userPointRepository.findById(1L)).thenReturn(Optional.of(userPoint));

        MyInfoResponse response = userService.getMyInfo(1L);
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getPoints()).isEqualTo(100);
    }

    @Test
    @DisplayName("내 정보 조회 실패 - 사용자 없음")
    void getMyInfo_notFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyInfo(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("사용자 프로필 조회 성공")
    void getUserProfile_success() {
        User user = User.builder().displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.countByUserAndIsDeleted(user, false)).thenReturn(5L);
        when(commentRepository.countByUser(user)).thenReturn(10L);

        UserProfileResponse response = userService.getUserProfile(1L);
        assertThat(response.getDisplayName()).isEqualTo("User");
        assertThat(response.getPostCount()).isEqualTo(5L);
        assertThat(response.getCommentCount()).isEqualTo(10L);
    }

    @Test
    @DisplayName("사용자 프로필 조회 실패 - 사용자 없음")
    void getUserProfile_notFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserProfile(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("프로필 업데이트 성공 - 이름 변경")
    void updateMyProfile_nameChange() {
        User user = User.builder().displayName("Old Name").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UpdateProfileResponse response = userService.updateMyProfile(1L, "New Name", null, null);

        assertThat(response.getDisplayName()).isEqualTo("New Name");
        verify(displayNameHistoryRepository).save(any());
    }

    @Test
    @DisplayName("프로필 업데이트 성공 - 이미지 변경")
    void updateMyProfile_imageChange() {
        User user = User.builder().displayName("Name").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.updateMyProfile(1L, null, "new.jpg", 100L);

        assertThat(user.getProfileImageUrl()).isEqualTo("new.jpg");
        verify(fileService).associateFileWithEntity(100L, 1L, "USER_PROFILE");
    }

    @Test
    @DisplayName("비밀번호 변경 성공")
    void updatePassword_success() {
        User user = User.builder().password("encodedOld").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "encodedOld")).thenReturn(true);
        when(passwordHistoryRepository.findTop3ByUserOrderByCreatedAtDesc(user)).thenReturn(Collections.emptyList());
        when(passwordEncoder.encode("new")).thenReturn("encodedNew");

        userService.updatePassword(1L, "old", "new");

        assertThat(user.getPassword()).isEqualTo("encodedNew");
        verify(passwordHistoryRepository).save(any());
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 현재 비밀번호 불일치")
    void updatePassword_wrongCurrent() {
        User user = User.builder().password("encodedOld").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encodedOld")).thenReturn(false);

        assertThatThrownBy(() -> userService.updatePassword(1L, "wrong", "new"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CURRENT_PASSWORD);
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 최근 사용한 비밀번호")
    void updatePassword_recentlyUsed() {
        User user = User.builder().password("encodedOld").build();
        PasswordHistory history = PasswordHistory.builder().passwordHash("encodedRecent").build();
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "encodedOld")).thenReturn(true);
        when(passwordHistoryRepository.findTop3ByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(history));
        when(passwordEncoder.matches("new", "encodedRecent")).thenReturn(true); // Same as recent

        assertThatThrownBy(() -> userService.updatePassword(1L, "old", "new"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_RECENTLY_USED);
    }

    @Test
    @DisplayName("회원 탈퇴 성공")
    void deleteAccount_success() {
        User user = User.builder().password("encodedPass").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass", "encodedPass")).thenReturn(true);

        userService.deleteAccount(1L, "pass");

        assertThat(user.getStatus()).isEqualTo("DELETED");
    }

    @Test
    @DisplayName("회원 탈퇴 실패 - 비밀번호 불일치")
    void deleteAccount_wrongPassword() {
        User user = User.builder().password("encodedPass").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encodedPass")).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteAccount(1L, "wrong"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PASSWORD);
    }

    @Test
    @DisplayName("내 댓글 조회")
    void getMyComments_success() {
        User user = User.builder().build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commentRepository.findByUserOrderByCreatedAtDesc(any(), any())).thenReturn(new PageImpl<>(Collections.emptyList()));

        Page<Comment> result = userService.getMyComments(1L, PageRequest.of(0, 10));
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("사용자 상태 변경 - 정지")
    void updateUserStatus_suspend() {
        User user = User.builder().build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.updateUserStatus(1L, "SUSPENDED");
        assertThat(user.getStatus()).isEqualTo("SUSPENDED");
    }

    @Test
    @DisplayName("사용자 상태 변경 - 활성화")
    void updateUserStatus_active() {
        User user = User.builder().build();
        user.suspend();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.updateUserStatus(1L, "ACTIVE");
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("사용자 상태 변경 실패 - 잘못된 값")
    void updateUserStatus_invalid() {
        User user = User.builder().build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.updateUserStatus(1L, "INVALID"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("사용자 설정 업데이트 (없으면 생성)")
    void updateSettings_create() {
        User user = User.builder().build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.empty());
        when(userSettingsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserSettings settings = userService.updateSettings(1L, "dark", "en", "UTC", true);
        
        assertThat(settings.getTheme()).isEqualTo("dark");
        assertThat(settings.getLanguage()).isEqualTo("en");
        assertThat(settings.getTimezone()).isEqualTo("UTC");
        assertThat(settings.getHideNsfw()).isTrue();
    }

    @Test
    @DisplayName("사용자 설정 조회 (없으면 기본값 반환)")
    void getSettings_default() {
        User user = User.builder().build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.empty());

        UserSettings settings = userService.getSettings(1L);
        assertThat(settings).isNotNull();
        // Default values check if needed
    }
    
    @Test
    @DisplayName("사용자 검색")
    void searchUsers() {
        when(userRepository.searchUsers(anyString(), any())).thenReturn(new PageImpl<>(Collections.emptyList()));
        Page<User> result = userService.searchUsers("query", PageRequest.of(0, 10));
        assertThat(result).isNotNull();
    }
}