package com.weedrice.whiteboard.domain.user.controller;

import com.weedrice.whiteboard.domain.board.dto.BoardResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.dto.*;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettings;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.domain.user.service.UserService;
import com.weedrice.whiteboard.domain.user.service.UserSettingsService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

        @Mock
        private UserService userService;

        @Mock
        private UserSettingsService userSettingsService;

        @Mock
        private UserBlockService userBlockService;

        @Mock
        private BoardService boardService;

        @Mock
        private PostService postService;

        @Mock
        private CommentService commentService;

        @InjectMocks
        private UserController userController;

        private User testUser;
        private CustomUserDetails customUserDetails;
        private Pageable pageable;

        @BeforeEach
        void setUp() {
                // 테스트용 User 객체 생성
                testUser = User.builder()
                                .loginId("testuser")
                                .displayName("Test User")
                                .email("test@example.com")
                                .password("encodedPassword")
                                .build();
                ReflectionTestUtils.setField(testUser, "userId", 1L);
                ReflectionTestUtils.setField(testUser, "isSuperAdmin", false);
                ReflectionTestUtils.setField(testUser, "isEmailVerified", true);
                ReflectionTestUtils.setField(testUser, "status", "ACTIVE");
                ReflectionTestUtils.setField(testUser, "createdAt", LocalDateTime.now());
                ReflectionTestUtils.setField(testUser, "lastLoginAt", LocalDateTime.now());

                // CustomUserDetails 생성
                customUserDetails = new CustomUserDetails(
                                1L,
                                "testuser",
                                "encodedPassword",
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

                pageable = PageRequest.of(0, 10);
        }

        @Nested
        @DisplayName("내 정보 관련 API")
        class MyInfoTests {

                @Test
                @DisplayName("내 정보 조회 API 성공")
                void getMyInfo_success() {
                        // given
                        given(userService.getMyInfo(1L)).willReturn(testUser);

                        // when
                        ResponseEntity<ApiResponse<MyInfoResponse>> response = userController
                                        .getMyInfo(customUserDetails);

                        // then
                        assertThat(response.getStatusCode().value()).isEqualTo(200);
                        assertThat(response.getBody()).isNotNull();
                        assertThat(response.getBody().isSuccess()).isTrue();
                        assertThat(response.getBody().getData()).isNotNull();
                        assertThat(response.getBody().getData().getUserId()).isEqualTo(1L);
                        assertThat(response.getBody().getData().getLoginId()).isEqualTo("testuser");
                        assertThat(response.getBody().getData().getDisplayName()).isEqualTo("Test User");
                        assertThat(response.getBody().getData().getEmail()).isEqualTo("test@example.com");
                        assertThat(response.getBody().getData().getStatus()).isEqualTo("ACTIVE");
                        assertThat(response.getBody().getData().getRole()).isEqualTo("USER");
                        assertThat(response.getBody().getData().getIsEmailVerified()).isTrue();
                }

                @Test
                @DisplayName("슈퍼 관리자 정보 조회 시 역할 확인")
                void getMyInfo_superAdmin_success() {
                        // given
                        ReflectionTestUtils.setField(testUser, "isSuperAdmin", true);
                        given(userService.getMyInfo(1L)).willReturn(testUser);

                        // when
                        ResponseEntity<ApiResponse<MyInfoResponse>> response = userController
                                        .getMyInfo(customUserDetails);

                        // then
                        assertThat(response.getBody().getData().getRole()).isEqualTo("SUPER_ADMIN");
                }

                @Test
                @DisplayName("프로필 수정 API 성공")
                void updateMyProfile_success() {
                        // given
                        UpdateProfileRequest request = new UpdateProfileRequest();
                        request.setDisplayName("Updated Name");
                        request.setProfileImageUrl("https://example.com/image.jpg");
                        request.setProfileImageId(100L);

                        User updatedUser = User.builder()
                                        .loginId("testuser")
                                        .displayName("Updated Name")
                                        .email("test@example.com")
                                        .build();
                        ReflectionTestUtils.setField(updatedUser, "userId", 1L);
                        ReflectionTestUtils.setField(updatedUser, "profileImageUrl", "https://example.com/image.jpg");

                        given(userService.updateMyProfile(eq(1L), eq("Updated Name"),
                                        eq("https://example.com/image.jpg"), eq(100L)))
                                        .willReturn(updatedUser);

                        // when
                        ResponseEntity<ApiResponse<UpdateProfileResponse>> response = userController
                                        .updateMyProfile(request, customUserDetails);

                        // then
                        assertThat(response.getStatusCode().value()).isEqualTo(200);
                        assertThat(response.getBody().isSuccess()).isTrue();
                        assertThat(response.getBody().getData().getDisplayName()).isEqualTo("Updated Name");
                }

                @Test
                @DisplayName("비밀번호 변경 API 성공")
                void updatePassword_success() {
                        // given
                        UpdatePasswordRequest request = new UpdatePasswordRequest();
                        ReflectionTestUtils.setField(request, "currentPassword", "oldPassword");
                        ReflectionTestUtils.setField(request, "newPassword", "newPassword123");

                        doNothing().when(userService).updatePassword(1L, "oldPassword", "newPassword123");

                        // when
                        ResponseEntity<ApiResponse<MessageResponse>> response = userController.updatePassword(request,
                                        customUserDetails);

                        // then
                        assertThat(response.getStatusCode().value()).isEqualTo(200);
                        assertThat(response.getBody().isSuccess()).isTrue();
                        assertThat(response.getBody().getData().getMessage()).isEqualTo("비밀번호가 변경되었습니다.");
                        verify(userService).updatePassword(1L, "oldPassword", "newPassword123");
                }

                @Test
                @DisplayName("회원 탈퇴 API 성공")
                void deleteAccount_success() {
                        // given
                        DeleteAccountRequest request = new DeleteAccountRequest();
                        request.setPassword("password123");

                        doNothing().when(userService).deleteAccount(1L, "password123");

                        // when
                        ResponseEntity<ApiResponse<MessageResponse>> response = userController.deleteAccount(request,
                                        customUserDetails);

                        // then
                        assertThat(response.getStatusCode().value()).isEqualTo(200);
                        assertThat(response.getBody().isSuccess()).isTrue();
                        assertThat(response.getBody().getData().getMessage()).isEqualTo("회원 탈퇴가 완료되었습니다.");
                        verify(userService).deleteAccount(1L, "password123");
                }
        }

        @Nested
        @DisplayName("사용자 프로필 조회 API")
        class UserProfileTests {

                @Test
                @DisplayName("사용자 프로필 조회 API 성공")
                void getUserProfile_success() {
                        // given
                        Long targetUserId = 2L;
                        UserProfileResponse mockProfile = UserProfileResponse.builder()
                                        .userId(targetUserId)
                                        .loginId("targetuser")
                                        .displayName("Target User")
                                        .postCount(5L)
                                        .commentCount(10L)
                                        .build();
                        given(userService.getUserProfile(targetUserId)).willReturn(mockProfile);

                        // when
                        ResponseEntity<ApiResponse<UserProfileResponse>> response = userController
                                        .getUserProfile(targetUserId);

                        // then
                        assertThat(response.getStatusCode().value()).isEqualTo(200);
                        assertThat(response.getBody()).isNotNull();
                        assertThat(response.getBody().isSuccess()).isTrue();
                        assertThat(response.getBody().getData()).isNotNull();
                        assertThat(response.getBody().getData().getUserId()).isEqualTo(targetUserId);
                        assertThat(response.getBody().getData().getLoginId()).isEqualTo("targetuser");
                        assertThat(response.getBody().getData().getPostCount()).isEqualTo(5L);
                }
        }

        @Nested
        @DisplayName("설정 관련 API")
        class SettingsTests {

                @Test
                @DisplayName("내 설정 조회 API 성공")
                void getMySettings_success() {
                        // given
                        UserSettings settings = UserSettings.builder().user(testUser).build();
                        given(userSettingsService.getSettings(1L)).willReturn(settings);

                        // when
                        ResponseEntity<ApiResponse<UserSettingsResponse>> response = userController
                                        .getMySettings(customUserDetails);

                        // then
                        assertThat(response.getStatusCode().value()).isEqualTo(200);
                        assertThat(response.getBody().isSuccess()).isTrue();
                        assertThat(response.getBody().getData().getTheme()).isEqualTo("LIGHT");
                        assertThat(response.getBody().getData().getLanguage()).isEqualTo("ko");
                        assertThat(response.getBody().getData().getTimezone()).isEqualTo("Asia/Seoul");
                        assertThat(response.getBody().getData().isHideNsfw()).isTrue();
                }

                @Test
                @DisplayName("내 설정 수정 API 성공")
                void updateMySettings_success() {
                        // given
                        UpdateSettingsRequest request = new UpdateSettingsRequest();
                        request.setTheme("DARK");
                        request.setLanguage("en");
                        request.setTimezone("America/New_York");
                        request.setHideNsfw(false);

                        UserSettings updatedSettings = UserSettings.builder().user(testUser).build();
                        updatedSettings.updateSettings("DARK", "en", "America/New_York", false);

                        given(userSettingsService.updateSettings(eq(1L), eq("DARK"), eq("en"), eq("America/New_York"),
                                        eq(false)))
                                        .willReturn(updatedSettings);

                        // when
                        ResponseEntity<ApiResponse<UserSettingsResponse>> response = userController
                                        .updateMySettings(request, customUserDetails);

                        // then
                        assertThat(response.getStatusCode().value()).isEqualTo(200);
                        assertThat(response.getBody().isSuccess()).isTrue();
                        assertThat(response.getBody().getData().getTheme()).isEqualTo("DARK");
                        assertThat(response.getBody().getData().getLanguage()).isEqualTo("en");
                }

                @Test
                @DisplayName("알림 설정 조회 API 성공")
                void getMyNotificationSettings_success() {
                        // given
                        UserNotificationSettings setting1 = UserNotificationSettings.builder()
                                        .userId(1L)
                                        .notificationType("COMMENT")
                                        .isEnabled(true)
                                        .build();
                        UserNotificationSettings setting2 = UserNotificationSettings.builder()
                                        .userId(1L)
                                        .notificationType("LIKE")
                                        .isEnabled(false)
                                        .build();

                        given(userSettingsService.getNotificationSettings(1L)).willReturn(List.of(setting1, setting2));

                        // when
                        ResponseEntity<ApiResponse<List<NotificationSettingResponse>>> response = userController
                                        .getMyNotificationSettings(customUserDetails);

                        // then
                        assertThat(response.getStatusCode().value()).isEqualTo(200);
                        assertThat(response.getBody().isSuccess()).isTrue();
                        assertThat(response.getBody().getData()).hasSize(2);
                        assertThat(response.getBody().getData().get(0).getNotificationType()).isEqualTo("COMMENT");
                        assertThat(response.getBody().getData().get(0).isEnabled()).isTrue();
                }

                @Test
                @DisplayName("알림 설정 수정 API 성공")
                void updateMyNotificationSetting_success() {
                        // given
                        UpdateNotificationSettingRequest request = new UpdateNotificationSettingRequest();
                        request.setNotificationType("COMMENT");
                        request.setIsEnabled(false);

                        UserNotificationSettings updatedSetting = UserNotificationSettings.builder()
                                        .userId(1L)
                                        .notificationType("COMMENT")
                                        .isEnabled(false)
                                        .build();

                        given(userSettingsService.updateNotificationSetting(1L, "COMMENT", false))
                                        .willReturn(updatedSetting);

                        // when
                        ResponseEntity<ApiResponse<NotificationSettingResponse>> response = userController
                                        .updateMyNotificationSetting(request, customUserDetails);

                        // then
                        assertThat(response.getStatusCode().value()).isEqualTo(200);
                        assertThat(response.getBody().isSuccess()).isTrue();
                        assertThat(response.getBody().getData().getNotificationType()).isEqualTo("COMMENT");
                        assertThat(response.getBody().getData().isEnabled()).isFalse();
                }
        }

        @Nested
        @DisplayName("사용자 차단 관련 API")
        class BlockUserTests {

                @Test
                @DisplayName("사용자 차단 API 성공")
                void blockUser_success() {
                        // given
                        Long targetUserId = 2L;
                        doNothing().when(userBlockService).blockUser(1L, targetUserId);

                        // when
                        ResponseEntity<ApiResponse<MessageResponse>> response = userController.blockUser(targetUserId,
                                        customUserDetails);

                        // then
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                        assertThat(response.getBody().isSuccess()).isTrue();
                        assertThat(response.getBody().getData().getMessage()).isEqualTo("차단되었습니다.");
                        verify(userBlockService).blockUser(1L, targetUserId);
                }

                @Test
                @DisplayName("사용자 차단 해제 API 성공")
                void unblockUser_success() {
                        // given
                        Long targetUserId = 2L;
                        doNothing().when(userBlockService).unblockUser(1L, targetUserId);

                        // when
                        ResponseEntity<ApiResponse<MessageResponse>> response = userController.unblockUser(targetUserId,
                                        customUserDetails);

                        // then
                        assertThat(response.getStatusCode().value()).isEqualTo(200);
                        assertThat(response.getBody().isSuccess()).isTrue();
                        assertThat(response.getBody().getData().getMessage()).isEqualTo("차단이 해제되었습니다.");
                        verify(userBlockService).unblockUser(1L, targetUserId);
                }

                @Test
                @DisplayName("차단 목록 조회 API 성공")
                void getBlockedUsers_success() {
                        // given
                        UserBlockService.BlockedUserDto blockedUser = new UserBlockService.BlockedUserDto(
                                        2L, "blockeduser", "Blocked User", LocalDateTime.now());
                        Page<UserBlockService.BlockedUserDto> blockedPage = new PageImpl<>(
                                        List.of(blockedUser), pageable, 1);

                        given(userBlockService.getBlockedUsers(1L, pageable)).willReturn(blockedPage);

                        // when
                        ResponseEntity<ApiResponse<PageResponse<BlockedUserResponse>>> response = userController
                                        .getBlockedUsers(pageable, customUserDetails);

                        // then
                        assertThat(response.getStatusCode().value()).isEqualTo(200);
                        assertThat(response.getBody().isSuccess()).isTrue();
                        assertThat(response.getBody().getData().getContent()).hasSize(1);
                        assertThat(response.getBody().getData().getContent().get(0).getLoginId())
                                        .isEqualTo("blockeduser");
                }
        }

        @Nested
        @DisplayName("구독 및 활동 내역 API")
        class ActivityTests {

                @Test
                @DisplayName("내 구독 목록 조회 API 성공")
                void getMySubscriptions_success() {
                        // given
                        Board board = Board.builder()
                                        .boardName("Test Board")
                                        .boardUrl("test-board")
                                        .creator(testUser)
                                        .build();
                        ReflectionTestUtils.setField(board, "boardId", 1L);

                        BoardResponse boardResponse = new BoardResponse(
                                        board, 100L, "Admin User", 1L, false, true, Collections.emptyList(),
                                        Collections.emptyList());
                        Page<BoardResponse> boardPage = new PageImpl<>(List.of(boardResponse), pageable, 1);

                        given(boardService.getMySubscriptions(1L, pageable)).willReturn(boardPage);

                        // when
                        ApiResponse<PageResponse<BoardResponse>> response = userController
                                        .getMySubscriptions(customUserDetails, pageable);

                        // then
                        assertThat(response.isSuccess()).isTrue();
                        assertThat(response.getData().getContent()).hasSize(1);
                        assertThat(response.getData().getContent().get(0).getBoardName()).isEqualTo("Test Board");
                }

                @Test
                @DisplayName("내 게시글 목록 조회 API 성공")
                void getMyPosts_success() {
                        // given
                        Board board = Board.builder()
                                        .boardName("Test Board")
                                        .boardUrl("test-board")
                                        .creator(testUser)
                                        .build();
                        ReflectionTestUtils.setField(board, "boardId", 1L);

                        Post post = Post.builder()
                                        .title("Test Post")
                                        .contents("Test Content")
                                        .user(testUser)
                                        .board(board)
                                        .build();
                        ReflectionTestUtils.setField(post, "postId", 1L);
                        ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.now());
                        ReflectionTestUtils.setField(post, "viewCount", 0);
                        ReflectionTestUtils.setField(post, "likeCount", 0);
                        ReflectionTestUtils.setField(post, "commentCount", 0);
                        ReflectionTestUtils.setField(post, "isNotice", false);
                        ReflectionTestUtils.setField(post, "isNsfw", false);
                        ReflectionTestUtils.setField(post, "isSpoiler", false);

                        Page<Post> postPage = new PageImpl<>(List.of(post), pageable, 1);

                        given(postService.getMyPosts(1L, pageable)).willReturn(postPage);

                        // when
                        ApiResponse<PageResponse<PostSummary>> response = userController.getMyPosts(customUserDetails,
                                        pageable);

                        // then
                        assertThat(response.isSuccess()).isTrue();
                        assertThat(response.getData().getContent()).hasSize(1);
                        assertThat(response.getData().getContent().get(0).getTitle()).isEqualTo("Test Post");
                }

                @Test
                @DisplayName("내 댓글 목록 조회 API 성공")
                void getMyComments_success() {
                        // given
                        Board board = Board.builder()
                                        .boardName("Test Board")
                                        .boardUrl("test-board")
                                        .creator(testUser)
                                        .build();
                        ReflectionTestUtils.setField(board, "boardId", 1L);

                        Post post = Post.builder()
                                        .title("Test Post")
                                        .contents("Test Content")
                                        .user(testUser)
                                        .board(board)
                                        .build();
                        ReflectionTestUtils.setField(post, "postId", 1L);

                        Comment comment = Comment.builder()
                                        .content("Test Comment")
                                        .user(testUser)
                                        .post(post)
                                        .build();
                        ReflectionTestUtils.setField(comment, "commentId", 1L);
                        ReflectionTestUtils.setField(comment, "createdAt", LocalDateTime.now());
                        ReflectionTestUtils.setField(comment, "likeCount", 0);

                        Page<Comment> commentPage = new PageImpl<>(List.of(comment), pageable, 1);

                        given(commentService.getMyComments(1L, pageable)).willReturn(commentPage);

                        // when
                        ApiResponse<PageResponse<com.weedrice.whiteboard.domain.comment.dto.MyCommentResponse>> response = userController
                                        .getMyComments(customUserDetails, pageable);

                        // then
                        assertThat(response.isSuccess()).isTrue();
                        assertThat(response.getData().getContent()).hasSize(1);
                }

                @Test
                @DisplayName("최근 본 게시글 목록 조회 API 성공")
                void getRecentlyViewedPosts_success() {
                        // given
                        PostSummary postSummary = PostSummary.builder()
                                        .postId(1L)
                                        .title("Viewed Post")
                                        .build();

                        Page<PostSummary> postSummaryPage = new PageImpl<>(List.of(postSummary), pageable, 1);

                        given(postService.getRecentlyViewedPosts(1L, pageable)).willReturn(postSummaryPage);

                        // when
                        ApiResponse<PageResponse<PostSummary>> response = userController
                                        .getRecentlyViewedPosts(customUserDetails, pageable);

                        // then
                        assertThat(response.isSuccess()).isTrue();
                        assertThat(response.getData().getContent()).hasSize(1);
                        assertThat(response.getData().getContent().get(0).getTitle()).isEqualTo("Viewed Post");
                }
        }
}
