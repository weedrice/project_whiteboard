package com.weedrice.whiteboard.domain.user.controller;

import com.weedrice.whiteboard.domain.agent.dto.AgentClaimRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentListResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentResponse;
import com.weedrice.whiteboard.domain.agent.service.AgentLifecycleService;
import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.board.dto.SubscriptionBoardResponse;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.comment.dto.MyCommentResponse;
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
import com.weedrice.whiteboard.domain.user.service.UserProfileService;
import com.weedrice.whiteboard.domain.user.service.UserSecurityService;
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
import org.springframework.data.domain.Sort;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

        @Mock
        private UserProfileService userProfileService;

        @Mock
        private UserSecurityService userSecurityService;

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

        @Mock
        private AgentLifecycleService agentLifecycleService;

        @Mock
        private org.springframework.context.MessageSource messageSource;

        @InjectMocks
        private UserController userController;

        private User testUser;
        private CustomUserDetails customUserDetails;
        private Pageable pageable;

        @BeforeEach
        void setUp() {
                // ?뚯뒪?몄슜 User 媛앹껜 ?앹꽦
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

                // CustomUserDetails ?앹꽦
                customUserDetails = new CustomUserDetails(
                                1L,
                                "testuser",
                                "encodedPassword",
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

                pageable = PageRequest.of(0, 10);

                // MessageSource 湲곕낯 mock ?ㅼ젙 (lenient濡??ㅼ젙?섏뿬 ?ъ슜?섏? ?딅뒗 ?뚯뒪?몄뿉?쒕룄 ?먮윭 諛쒖깮?섏? ?딅룄濡?
                lenient().when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(invocation -> {
                        String code = invocation.getArgument(0);
                        if ("success.user.passwordChanged".equals(code)) {
                                return "鍮꾨?踰덊샇媛 蹂寃쎈릺?덉뒿?덈떎.";
                        } else if ("success.user.accountDeleted".equals(code)) {
                                return "?뚯썝 ?덊눜媛 ?꾨즺?섏뿀?듬땲??";
                        } else if ("success.user.blocked".equals(code)) {
                                return "李⑤떒?섏뿀?듬땲??";
                        } else if ("success.user.unblocked".equals(code)) {
                                return "李⑤떒???댁젣?섏뿀?듬땲??";
                        }
                        return code;
                });
        }

        @Nested
        @DisplayName("???뺣낫 愿??API")
        class MyInfoTests {

                @Test
                @DisplayName("?뚮┝ ?ㅼ젙 bulk ?섏젙 API ?깃났")
                void updateMyNotificationSettings_success() {
                        // given
                        UpdateNotificationSettingsRequest request = new UpdateNotificationSettingsRequest();
                        request.setSettings(List.of(
                                        new UpdateNotificationSettingItem("LIKE", true),
                                        new UpdateNotificationSettingItem("COMMENT", false),
                                        new UpdateNotificationSettingItem("REPLY", true)));

                        List<NotificationSettingResponse> updatedSettings = List.of(
                                        new NotificationSettingResponse("LIKE", true),
                                        new NotificationSettingResponse("COMMENT", false),
                                        new NotificationSettingResponse("REPLY", true));

                        given(userSettingsService.updateNotificationSettings(eq(1L), anyList()))
                                        .willReturn(updatedSettings);

                        // when
                        ResponseEntity<ApiResponse<List<NotificationSettingResponse>>> response = userController
                                        .updateMyNotificationSettings(request, customUserDetails);

                        // then
                        assertThat(response.getStatusCode().value()).isEqualTo(200);
                        assertThat(response.getBody().isSuccess()).isTrue();
                        assertThat(response.getBody().getData()).hasSize(3);
                        assertThat(response.getBody().getData())
                                        .extracting(NotificationSettingResponse::getNotificationType)
                                        .containsExactly("LIKE", "COMMENT", "REPLY");
                        assertThat(response.getBody().getData())
                                        .extracting(NotificationSettingResponse::isEnabled)
                                        .containsExactly(true, false, true);
                        verify(userSettingsService).updateNotificationSettings(eq(1L), eq(request.getSettings()));
                }
        }

        @Nested
        @DisplayName("?ъ슜??李⑤떒 愿??API")
        class BlockUserTests {

                @Test
                @DisplayName("?ъ슜??李⑤떒 API ?깃났")
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
                        assertThat(response.getBody().getData().getMessage()).isEqualTo("李⑤떒?섏뿀?듬땲??");
                        verify(userBlockService).blockUser(1L, targetUserId);
                }

                @Test
                @DisplayName("?ъ슜??李⑤떒 ?댁젣 API ?깃났")
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
                        assertThat(response.getBody().getData().getMessage()).isEqualTo("李⑤떒???댁젣?섏뿀?듬땲??");
                        verify(userBlockService).unblockUser(1L, targetUserId);
                }

                @Test
                @DisplayName("李⑤떒 紐⑸줉 議고쉶 API ?깃났")
                void getBlockedUsers_success() {
                        // given
                        BlockedUserResponse blockedUser = new BlockedUserResponse(
                                        2L, "blockeduser", "Blocked User", LocalDateTime.now());
                        Page<BlockedUserResponse> blockedPage = new PageImpl<>(
                                        List.of(blockedUser), pageable, 1);

                        given(userBlockService.getBlockedUsers(1L, pageable)).willReturn(blockedPage);

                        // when
                        ResponseEntity<ApiResponse<PageResponse<BlockedUserResponse>>> response = userController
                                        .getBlockedUsers(0, 10, Sort.unsorted(), customUserDetails);

                        // then
                        assertThat(response.getStatusCode().value()).isEqualTo(200);
                        assertThat(response.getBody().isSuccess()).isTrue();
                        assertThat(response.getBody().getData().getContent()).hasSize(1);
                        assertThat(response.getBody().getData().getContent().get(0).getLoginId())
                                        .isEqualTo("blockeduser");
                }
        }

        @Nested
        @DisplayName("援щ룆 諛??쒕룞 ?댁뿭 API")
        class ActivityTests {

                @Test
                @DisplayName("??援щ룆 紐⑸줉 議고쉶 API ?깃났")
                void getMySubscriptions_success() {
                        // given
                        Board board = Board.builder()
                                        .boardName("Test Board")
                                        .boardUrl("test-board")
                                        .creator(testUser)
                                        .build();
                        ReflectionTestUtils.setField(board, "boardId", 1L);

                        BoardListResponse boardResponse = new BoardListResponse(
                                        board, 100L, 0L, "Admin User", true);
                        SubscriptionBoardResponse subscriptionResponse = SubscriptionBoardResponse.accessible(boardResponse);
                        Page<SubscriptionBoardResponse> boardPage = new PageImpl<>(
                                        List.of(subscriptionResponse),
                                        pageable,
                                        1);

                        given(boardService.getMySubscriptions(1L, pageable, false)).willReturn(boardPage);

                        // when
                        ApiResponse<PageResponse<SubscriptionBoardResponse>> response = userController
                                        .getMySubscriptions(customUserDetails, false, 0, 10, Sort.unsorted());

                        // then
                        assertThat(response.isSuccess()).isTrue();
                        assertThat(response.getData().getContent()).hasSize(1);
                        assertThat(response.getData().getContent().get(0).getBoardName()).isEqualTo("Test Board");
                        assertThat(response.getData().getContent().get(0).getAccessState())
                                        .isEqualTo(SubscriptionBoardResponse.AccessState.ACCESSIBLE);
                }

                @Test
                @DisplayName("??寃뚯떆湲 紐⑸줉 議고쉶 API ?깃났")
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

                        PostSummary postSummary = PostSummary.from(post);
                        Page<PostSummary> postPage = new PageImpl<>(List.of(postSummary), pageable, 1);

                        given(postService.getMyPosts(1L, pageable)).willReturn(postPage);

                        // when
                        ApiResponse<PageResponse<PostSummary>> response = userController.getMyPosts(customUserDetails,
                                        0, 10, Sort.unsorted());

                        // then
                        assertThat(response.isSuccess()).isTrue();
                        assertThat(response.getData().getContent()).hasSize(1);
                        assertThat(response.getData().getContent().get(0).getTitle()).isEqualTo("Test Post");
                }

                @Test
                @DisplayName("???볤? 紐⑸줉 議고쉶 API ?깃났")
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

                        MyCommentResponse myCommentResponse = MyCommentResponse.from(comment);
                        Page<MyCommentResponse> commentPage = new PageImpl<>(List.of(myCommentResponse), pageable, 1);

                        given(commentService.getMyComments(1L, pageable)).willReturn(commentPage);

                        // when
                        ApiResponse<PageResponse<com.weedrice.whiteboard.domain.comment.dto.MyCommentResponse>> response = userController
                                        .getMyComments(customUserDetails, 0, 10, Sort.unsorted());

                        // then
                        assertThat(response.isSuccess()).isTrue();
                        assertThat(response.getData().getContent()).hasSize(1);
                }

                @Test
                @DisplayName("理쒓렐 蹂?寃뚯떆湲 紐⑸줉 議고쉶 API ?깃났")
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
                                        .getRecentlyViewedPosts(customUserDetails, 0, 10, Sort.unsorted());

                        // then
                        assertThat(response.isSuccess()).isTrue();
                        assertThat(response.getData().getContent()).hasSize(1);
                        assertThat(response.getData().getContent().get(0).getTitle()).isEqualTo("Viewed Post");
                }
        }

        @Nested
        @DisplayName("공개 프로필 API")
        class UserProfileTests {

                @Test
                @DisplayName("인증 사용자의 공개 프로필 조회는 viewer id를 전달한다")
                void getUserProfile_authenticatedViewer_passesViewerId() {
                        UserProfileResponse profile = UserProfileResponse.builder()
                                        .userId(2L)
                                        .displayName("Target")
                                        .postCount(3L)
                                        .commentCount(4L)
                                        .build();
                        given(userProfileService.getUserProfile(2L, 1L)).willReturn(profile);

                        ResponseEntity<ApiResponse<UserProfileResponse>> response =
                                        userController.getUserProfile(2L, customUserDetails);

                        assertThat(response.getStatusCode().value()).isEqualTo(200);
                        assertThat(response.getBody().isSuccess()).isTrue();
                        assertThat(response.getBody().getData().getUserId()).isEqualTo(2L);
                        verify(userProfileService).getUserProfile(2L, 1L);
                }

                @Test
                @DisplayName("익명 공개 프로필 조회는 null viewer id를 전달한다")
                void getUserProfile_anonymousViewer_passesNullViewerId() {
                        UserProfileResponse profile = UserProfileResponse.builder()
                                        .userId(2L)
                                        .displayName("Target")
                                        .build();
                        given(userProfileService.getUserProfile(2L, null)).willReturn(profile);

                        ResponseEntity<ApiResponse<UserProfileResponse>> response =
                                        userController.getUserProfile(2L, null);

                        assertThat(response.getStatusCode().value()).isEqualTo(200);
                        assertThat(response.getBody().isSuccess()).isTrue();
                        assertThat(response.getBody().getData().getUserId()).isEqualTo(2L);
                        verify(userProfileService).getUserProfile(2L, null);
                }
        }

        @Nested
        @DisplayName("Agent API")
        class AgentTests {

                @Test
                @DisplayName("??Agent claim API ?깃났")
                void claimAgent_success() {
                        AgentClaimRequest request = new AgentClaimRequest();
                        ReflectionTestUtils.setField(request, "agentToken", "noviis_agt_token");

                        AgentResponse agentResponse = AgentResponse.builder()
                                        .agentId(10L)
                                        .name("Writer Agent")
                                        .description("desc")
                                        .status("ACTIVE")
                                        .createdAt(LocalDateTime.now())
                                        .claimedAt(LocalDateTime.now())
                                        .build();

                        given(agentLifecycleService.claim(eq(1L), any(AgentClaimRequest.class), any()))
                                        .willReturn(agentResponse);

                        ApiResponse<AgentResponse> response = userController.claimAgent(request, customUserDetails, null);

                        assertThat(response.isSuccess()).isTrue();
                        assertThat(response.getData().getAgentId()).isEqualTo(10L);
                        assertThat(response.getData().getStatus()).isEqualTo("ACTIVE");
                        verify(agentLifecycleService).claim(eq(1L), any(AgentClaimRequest.class), any());
                }

                @Test
                @DisplayName("??Agent 紐⑸줉 議고쉶 API ?깃났")
                void getMyAgents_success() {
                        AgentResponse first = AgentResponse.builder()
                                        .agentId(10L)
                                        .name("Writer Agent")
                                        .description("desc")
                                        .status("ACTIVE")
                                        .createdAt(LocalDateTime.now())
                                        .build();
                        AgentResponse second = AgentResponse.builder()
                                        .agentId(11L)
                                        .name("Comment Agent")
                                        .description("desc2")
                                        .status("SUSPENDED")
                                        .createdAt(LocalDateTime.now())
                                        .build();

                        given(agentLifecycleService.getMyAgents(1L))
                                        .willReturn(new AgentListResponse(List.of(first, second)));

                        ApiResponse<AgentListResponse> response = userController.getMyAgents(customUserDetails);

                        assertThat(response.isSuccess()).isTrue();
                        assertThat(response.getData().getAgents()).hasSize(2);
                        assertThat(response.getData().getAgents().get(0).getName()).isEqualTo("Writer Agent");
                }

                @Test
                @DisplayName("??Agent ?뺤? API ?깃났")
                void suspendMyAgent_success() {
                        AgentResponse agentResponse = AgentResponse.builder()
                                        .agentId(10L)
                                        .name("Writer Agent")
                                        .description("desc")
                                        .status("SUSPENDED")
                                        .createdAt(LocalDateTime.now())
                                        .build();

                        given(agentLifecycleService.suspendMyAgent(eq(1L), eq(10L), any()))
                                        .willReturn(agentResponse);

                        ApiResponse<AgentResponse> response = userController.suspendMyAgent(10L, customUserDetails, null);

                        assertThat(response.isSuccess()).isTrue();
                        assertThat(response.getData().getStatus()).isEqualTo("SUSPENDED");
                }

                @Test
                @DisplayName("Agent 재활성화 API 성공")
                void activateMyAgent_success() {
                        AgentResponse agentResponse = AgentResponse.builder()
                                        .agentId(10L)
                                        .name("Writer Agent")
                                        .description("desc")
                                        .status("ACTIVE")
                                        .createdAt(LocalDateTime.now())
                                        .build();

                        given(agentLifecycleService.activateMyAgent(eq(1L), eq(10L), any()))
                                        .willReturn(agentResponse);

                        ApiResponse<AgentResponse> response = userController.activateMyAgent(10L, customUserDetails, null);

                        assertThat(response.isSuccess()).isTrue();
                        assertThat(response.getData().getStatus()).isEqualTo("ACTIVE");
                        verify(agentLifecycleService).activateMyAgent(eq(1L), eq(10L), any());
                }

                @Test
                @DisplayName("??Agent ??젣 API ?깃났")
                void deleteMyAgent_success() {
                        doNothing().when(agentLifecycleService).deleteMyAgent(eq(1L), eq(10L), any());

                        ApiResponse<Void> response = userController.deleteMyAgent(10L, customUserDetails, null);

                        assertThat(response.isSuccess()).isTrue();
                        assertThat(response.getData()).isNull();
                        verify(agentLifecycleService).deleteMyAgent(eq(1L), eq(10L), any());
                }
        }
}

