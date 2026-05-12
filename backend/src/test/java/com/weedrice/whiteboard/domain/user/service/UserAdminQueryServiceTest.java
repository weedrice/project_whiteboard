package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.dto.AdminUserCommentResponse;
import com.weedrice.whiteboard.domain.user.dto.AdminUserDetailResponse;
import com.weedrice.whiteboard.domain.user.dto.AdminUserPostResponse;
import com.weedrice.whiteboard.domain.user.dto.AdminUserSubscriptionResponse;
import com.weedrice.whiteboard.domain.user.dto.UserAdminResponse;
import com.weedrice.whiteboard.domain.user.dto.UserAdminSearchCondition;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
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
    @Mock private BoardSubscriptionRepository boardSubscriptionRepository;
    @Mock private AdminUserDetailStatsReader adminUserDetailStatsReader;
    @Mock private BoardAccessPolicy boardAccessPolicy;

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
    @DisplayName("searchUsersForAdmin resolves display role by priority")
    void searchUsersForAdmin_resolvesRoleByPriority() {
        User adminUser = User.builder().loginId("admin").email("admin@test.com").password("pw").displayName("admin").build();
        ReflectionTestUtils.setField(adminUser, "userId", 1L);

        Page<User> page = new PageImpl<>(List.of(adminUser), PageRequest.of(0, 20), 1);
        Admin moderator = admin(adminUser, "MODERATOR", 30L);
        Admin boardAdmin = admin(adminUser, "BOARD_ADMIN", 40L);
        Admin siteAdmin = admin(adminUser, "ADMIN", 20L);

        when(userRepository.searchUsersForAdmin(anyString(), any(), any())).thenReturn(page);
        when(adminRepository.findByUserUserIdInAndIsActiveOrderByAdminIdAsc(List.of(1L), true))
                .thenReturn(List.of(moderator, boardAdmin, siteAdmin));

        Page<UserAdminResponse> response = userAdminQueryService.searchUsersForAdmin(
                "query", null, null, null, null, null,
                null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(response.getContent()).extracting(UserAdminResponse::getRole)
                .containsExactly("ADMIN");
    }

    @Test
    @DisplayName("searchUsersForAdmin normalizes role and status filters")
    void searchUsersForAdmin_normalizesRoleAndStatusFilters() {
        when(userRepository.searchUsersForAdmin(isNull(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        userAdminQueryService.searchUsersForAdmin(
                null, " active ", " moderator ", null, null, null,
                null, null, null, null, null, PageRequest.of(0, 20));

        ArgumentCaptor<UserAdminSearchCondition> conditionCaptor =
                ArgumentCaptor.forClass(UserAdminSearchCondition.class);
        verify(userRepository).searchUsersForAdmin(isNull(), conditionCaptor.capture(), any());
        assertThat(conditionCaptor.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(conditionCaptor.getValue().getRole()).isEqualTo("MODERATOR");
    }

    @Test
    @DisplayName("searchUsersForAdmin caps page size and drops unsupported sort")
    void searchUsersForAdmin_normalizesPageSizeAndSort() {
        when(userRepository.searchUsersForAdmin(isNull(), any(), any()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(2), 0));

        Pageable requestedPageable = PageRequest.of(
                2,
                1000,
                Sort.by(
                        Sort.Order.asc("createdAt"),
                        Sort.Order.desc("unsupported")));
        Page<UserAdminResponse> response = userAdminQueryService.searchUsersForAdmin(
                null, null, null, null, null, null,
                null, null, null, null, null, requestedPageable);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchUsersForAdmin(isNull(), any(), pageableCaptor.capture());
        Pageable safePageable = pageableCaptor.getValue();
        assertThat(safePageable.getPageNumber()).isEqualTo(2);
        assertThat(safePageable.getPageSize()).isEqualTo(100);
        assertThat(safePageable.getSort()).containsExactly(Sort.Order.asc("createdAt"));
        assertThat(response.getPageable()).isEqualTo(safePageable);
    }

    @Test
    @DisplayName("searchUsersForAdmin uses default sort when requested sort is unsupported")
    void searchUsersForAdmin_usesDefaultSortWhenSortUnsupported() {
        when(userRepository.searchUsersForAdmin(isNull(), any(), any()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(2), 0));

        Page<UserAdminResponse> response = userAdminQueryService.searchUsersForAdmin(
                null, null, null, null, null, null,
                null, null, null, null, null, PageRequest.of(0, 20, Sort.by("unsupported")));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchUsersForAdmin(isNull(), any(), pageableCaptor.capture());
        Pageable safePageable = pageableCaptor.getValue();
        assertThat(safePageable.getSort()).containsExactly(Sort.Order.desc("userId"));
        assertThat(response.getPageable()).isEqualTo(safePageable);
    }

    @Test
    @DisplayName("searchUsersForAdmin uses default pageable when pageable is unpaged")
    void searchUsersForAdmin_usesDefaultPageableWhenUnpaged() {
        when(userRepository.searchUsersForAdmin(isNull(), any(), any()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(2), 0));

        Page<UserAdminResponse> response = userAdminQueryService.searchUsersForAdmin(
                null, null, null, null, null, null,
                null, null, null, null, null, Pageable.unpaged());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchUsersForAdmin(isNull(), any(), pageableCaptor.capture());
        Pageable safePageable = pageableCaptor.getValue();
        assertThat(safePageable.getPageNumber()).isZero();
        assertThat(safePageable.getPageSize()).isEqualTo(20);
        assertThat(safePageable.getSort()).containsExactly(Sort.Order.desc("userId"));
        assertThat(response.getPageable()).isEqualTo(safePageable);
    }

    @Test
    @DisplayName("searchUsersForAdmin rejects invalid role filter")
    void searchUsersForAdmin_rejectsInvalidRoleFilter() {
        assertThatThrownBy(() -> userAdminQueryService.searchUsersForAdmin(
                null, null, "BOARDADMIN", null, null, null,
                null, null, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(userRepository, never()).searchUsersForAdmin(any(), any(), any());
    }

    @Test
    @DisplayName("searchUsersForAdmin rejects invalid status filter")
    void searchUsersForAdmin_rejectsInvalidStatusFilter() {
        assertThatThrownBy(() -> userAdminQueryService.searchUsersForAdmin(
                null, "LOCKED", null, null, null, null,
                null, null, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(userRepository, never()).searchUsersForAdmin(any(), any(), any());
    }

    @Test
    @DisplayName("getUserDetailForAdmin assembles detail response from stats reader")
    void getUserDetailForAdmin_usesStatsReader() {
        User user = User.builder()
                .loginId("target")
                .email("target@test.com")
                .password("pw")
                .displayName("target")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRepository.findByUserUserIdInAndIsActiveOrderByAdminIdAsc(List.of(1L), true))
                .thenReturn(List.of());
        when(adminUserDetailStatsReader.read(user))
                .thenReturn(new AdminUserDetailStatsReader.AdminUserDetailStats(
                        3L,
                        4L,
                        5L,
                        null,
                        6L,
                        null,
                        7L,
                        8L));

        AdminUserDetailResponse response = userAdminQueryService.getUserDetailForAdmin(1L);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getRole()).isEqualTo("USER");
        assertThat(response.getPostCount()).isEqualTo(3L);
        assertThat(response.getCommentCount()).isEqualTo(4L);
        assertThat(response.getSubscriptionCount()).isEqualTo(5L);
        assertThat(response.getRecentLogin()).isNull();
        assertThat(response.getSanctionSummary().getCount()).isEqualTo(6L);
        assertThat(response.getReportSummary().getTotalCount()).isEqualTo(7L);
        assertThat(response.getReportSummary().getPendingCount()).isEqualTo(8L);
        verify(adminUserDetailStatsReader).read(user);
    }

    @Test
    @DisplayName("getUserDetailForAdmin resolves display role by priority")
    void getUserDetailForAdmin_resolvesRoleByPriority() {
        User user = User.builder()
                .loginId("target")
                .email("target@test.com")
                .password("pw")
                .displayName("target")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        Admin moderator = admin(user, "MODERATOR", 30L);
        Admin boardAdmin = admin(user, "BOARD_ADMIN", 40L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRepository.findByUserUserIdInAndIsActiveOrderByAdminIdAsc(List.of(1L), true))
                .thenReturn(List.of(moderator, boardAdmin));
        when(adminUserDetailStatsReader.read(user))
                .thenReturn(new AdminUserDetailStatsReader.AdminUserDetailStats(
                        0L,
                        0L,
                        0L,
                        null,
                        0L,
                        null,
                        0L,
                        0L));

        AdminUserDetailResponse response = userAdminQueryService.getUserDetailForAdmin(1L);

        assertThat(response.getRole()).isEqualTo("BOARD_ADMIN");
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

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
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

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
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

        User owner = User.builder().loginId("owner").email("owner@test.com").password("pw").displayName("owner").build();
        ReflectionTestUtils.setField(owner, "userId", 2L);

        Board board = Board.builder()
                .boardName("Hidden")
                .boardUrl("hidden")
                .description("desc")
                .creator(owner)
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

        Board adminBoard = Board.builder()
                .boardName("Admin")
                .boardUrl("admin")
                .description("desc")
                .creator(owner)
                .sortOrder(4)
                .isPublic(false)
                .build();
        ReflectionTestUtils.setField(adminBoard, "boardId", 14L);

        BoardSubscription adminSubscription = BoardSubscription.builder()
                .user(user)
                .board(adminBoard)
                .role("MEMBER")
                .sortOrder(8)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardSubscriptionRepository.findByUserOrderBySortOrderAsc(user, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(subscription, adminSubscription), PageRequest.of(0, 10), 2));
        when(adminRepository.findActiveBoardIdsByUser(user)).thenReturn(List.of(14L));
        when(boardAccessPolicy.canReadBoard(board, user, Set.of(14L))).thenReturn(false);
        when(boardAccessPolicy.canReadBoard(adminBoard, user, Set.of(14L))).thenReturn(true);

        Page<AdminUserSubscriptionResponse> response = userAdminQueryService.getUserSubscriptionsForAdmin(1L, PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0)).satisfies(item -> {
            assertThat(item.getBoardId()).isEqualTo(13L);
            assertThat(item.isBoardActive()).isFalse();
            assertThat(item.isBoardPublic()).isFalse();
            assertThat(item.isSubscriptionAccessible()).isFalse();
            assertThat(item.getInaccessibleReason()).isEqualTo("INACTIVE");
        });
        assertThat(response.getContent().get(1)).satisfies(item -> {
            assertThat(item.getBoardId()).isEqualTo(14L);
            assertThat(item.isBoardActive()).isTrue();
            assertThat(item.isBoardPublic()).isFalse();
            assertThat(item.isSubscriptionAccessible()).isTrue();
            assertThat(item.getInaccessibleReason()).isNull();
        });
        verify(adminRepository).findActiveBoardIdsByUser(user);
        verify(boardAccessPolicy).canReadBoard(board, user, Set.of(14L));
        verify(boardAccessPolicy).canReadBoard(adminBoard, user, Set.of(14L));
    }

    private Admin admin(User user, String role, Long adminId) {
        Admin targetAdmin = Admin.builder()
                .user(user)
                .board(null)
                .role(role)
                .build();
        ReflectionTestUtils.setField(targetAdmin, "adminId", adminId);
        return targetAdmin;
    }
}
