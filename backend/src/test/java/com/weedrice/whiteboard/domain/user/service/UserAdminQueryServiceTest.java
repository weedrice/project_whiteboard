package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.admin.service.ModerationActorResolver;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.dto.AdminUserCommentResponse;
import com.weedrice.whiteboard.domain.user.dto.AdminUserPostResponse;
import com.weedrice.whiteboard.domain.user.dto.AdminUserSubscriptionResponse;
import com.weedrice.whiteboard.domain.user.dto.UserAdminResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminQueryServiceTest {

    @InjectMocks
    private UserAdminQueryService userAdminQueryService;

    @Mock private UserRepository userRepository;
    @Mock private PostRepository postRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private ModerationActorResolver moderationActorResolver;
    @Mock private BoardAccessPolicy boardAccessPolicy;
    @Mock private BoardSubscriptionRepository boardSubscriptionRepository;
    @Mock private LoginHistoryRepository loginHistoryRepository;
    @Mock private SanctionRepository sanctionRepository;
    @Mock private ReportRepository reportRepository;

    @Test
    @DisplayName("searchUsersForAdmin resolves roles in batch")
    void searchUsersForAdmin_resolvesRolesInBatch() {
        User superAdmin = User.builder().loginId("super").email("super@test.com").password("pw").displayName("super").build();
        ReflectionTestUtils.setField(superAdmin, "userId", 1L);
        ReflectionTestUtils.setField(superAdmin, "isSuperAdmin", true);

        User moderator = User.builder().loginId("mod").email("mod@test.com").password("pw").displayName("mod").build();
        ReflectionTestUtils.setField(moderator, "userId", 2L);

        Page<User> page = new PageImpl<>(List.of(superAdmin, moderator), PageRequest.of(0, 20), 2);
        Admin activeAdmin = Admin.builder().user(moderator).board(null).role("MODERATOR").build();

        when(userRepository.searchUsersForAdmin(anyString(), any(), any())).thenReturn(page);
        when(adminRepository.findByUserUserIdInAndIsActiveOrderByAdminIdAsc(List.of(2L), true))
                .thenReturn(List.of(activeAdmin));

        Page<UserAdminResponse> response = userAdminQueryService.searchUsersForAdmin(
                "query", null, null, null, null, null,
                null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(response.getContent()).extracting(UserAdminResponse::getRole)
                .containsExactly("SUPER_ADMIN", "MODERATOR");
        verify(adminRepository).findByUserUserIdInAndIsActiveOrderByAdminIdAsc(List.of(2L), true);
    }

    @Test
    @DisplayName("getUserPostsForAdmin returns admin-specific post metadata including deleted items")
    void getUserPostsForAdmin_returnsAdminSpecificPostMetadata() {
        User user = User.builder().loginId("writer").email("writer@test.com").password("pw").displayName("writer").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder()
                .boardName("Free")
                .boardUrl("free")
                .description("desc")
                .creator(user)
                .sortOrder(1)
                .isPublic(true)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 11L);

        Post post = Post.builder()
                .board(board)
                .user(user)
                .title("deleted post")
                .contents("content")
                .isNotice(true)
                .isNsfw(true)
                .isSpoiler(false)
                .isSecret(true)
                .build();
        ReflectionTestUtils.setField(post, "postId", 21L);
        ReflectionTestUtils.setField(post, "isDeleted", true);

        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
        when(postRepository.findByUserOrderByCreatedAtDesc(user, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1));

        Page<AdminUserPostResponse> response = userAdminQueryService.getUserPostsForAdmin(1L, PageRequest.of(0, 10));

        assertThat(response.getContent()).singleElement().satisfies(item -> {
            assertThat(item.getPostId()).isEqualTo(21L);
            assertThat(item.isDeleted()).isTrue();
            assertThat(item.isNotice()).isTrue();
            assertThat(item.isNsfw()).isTrue();
            assertThat(item.isSecret()).isTrue();
            assertThat(item.getBoardName()).isEqualTo("Free");
        });
    }

    @Test
    @DisplayName("getUserCommentsForAdmin returns deleted flag and related post metadata")
    void getUserCommentsForAdmin_returnsAdminSpecificCommentMetadata() {
        User user = User.builder().loginId("writer").email("writer@test.com").password("pw").displayName("writer").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder()
                .boardName("Private")
                .boardUrl("private")
                .description("desc")
                .creator(user)
                .sortOrder(2)
                .isPublic(false)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 12L);

        Post post = Post.builder()
                .board(board)
                .user(user)
                .title("post")
                .contents("content")
                .isNotice(false)
                .isNsfw(false)
                .isSpoiler(false)
                .isSecret(false)
                .build();
        ReflectionTestUtils.setField(post, "postId", 22L);
        ReflectionTestUtils.setField(post, "isDeleted", true);

        Comment parent = Comment.builder().post(post).user(user).parent(null).depth(0).content("parent").build();
        ReflectionTestUtils.setField(parent, "commentId", 30L);

        Comment comment = Comment.builder().post(post).user(user).parent(parent).depth(1).content("reply").build();
        ReflectionTestUtils.setField(comment, "commentId", 31L);
        ReflectionTestUtils.setField(comment, "isDeleted", true);

        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
        when(commentRepository.findByUserOrderByCreatedAtDesc(user, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(comment), PageRequest.of(0, 10), 1));

        Page<AdminUserCommentResponse> response = userAdminQueryService.getUserCommentsForAdmin(1L, PageRequest.of(0, 10));

        assertThat(response.getContent()).singleElement().satisfies(item -> {
            assertThat(item.getCommentId()).isEqualTo(31L);
            assertThat(item.isDeleted()).isTrue();
            assertThat(item.getDepth()).isEqualTo(1);
            assertThat(item.getParentId()).isEqualTo(30L);
            assertThat(item.getPost().isDeleted()).isTrue();
            assertThat(item.getPost().isBoardPublic()).isFalse();
        });
    }

    @Test
    @DisplayName("getUserSubscriptionsForAdmin returns operational accessibility metadata")
    void getUserSubscriptionsForAdmin_returnsAdminSpecificSubscriptionMetadata() {
        User user = User.builder().loginId("writer").email("writer@test.com").password("pw").displayName("writer").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder()
                .boardName("Hidden")
                .boardUrl("hidden")
                .description("desc")
                .creator(user)
                .sortOrder(3)
                .isPublic(false)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 13L);
        ReflectionTestUtils.setField(board, "isActive", false);

        BoardSubscription subscription = BoardSubscription.builder()
                .user(user)
                .board(board)
                .role("MEMBER")
                .sortOrder(7)
                .build();

        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
        when(boardSubscriptionRepository.findByUserOrderBySortOrderAsc(user, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(subscription), PageRequest.of(0, 10), 1));
        when(boardAccessPolicy.canReadBoard(board, user)).thenReturn(false);

        Page<AdminUserSubscriptionResponse> response = userAdminQueryService.getUserSubscriptionsForAdmin(1L, PageRequest.of(0, 10));

        assertThat(response.getContent()).singleElement().satisfies(item -> {
            assertThat(item.getBoardId()).isEqualTo(13L);
            assertThat(item.isBoardActive()).isFalse();
            assertThat(item.isBoardPublic()).isFalse();
            assertThat(item.isSubscriptionAccessible()).isFalse();
            assertThat(item.getInaccessibleReason()).isEqualTo("INACTIVE");
        });
    }
}
