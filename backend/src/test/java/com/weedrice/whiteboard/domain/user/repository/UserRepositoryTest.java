package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.entity.ReportTargetType;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.user.dto.UserAdminSearchCondition;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(QuerydslConfig.class)
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private User user1;
    private User user3;
    private Board board;

    @BeforeEach
    void setUp() {
        user1 = User.builder().loginId("testuser1").displayName("Apple User").email("test1@test.com").password("pass").build();
        User user2 = User.builder().loginId("testuser2").displayName("Banana User").email("test2@test.com").password("pass").build();
        user3 = User.builder().loginId("another").displayName("Apple Another").email("test3@test.com").password("pass").build();

        entityManager.persist(user1);
        entityManager.persist(user2);
        entityManager.persist(user3);
        board = Board.builder()
                .boardName("free")
                .boardUrl("free")
                .creator(user1)
                .build();
        entityManager.persist(board);
        entityManager.flush();
    }

    @Test
    @DisplayName("사용자 검색 성공 - 로그인 ID로 검색")
    void searchUsers_byLoginId() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<User> result = userRepository.searchUsers("testuser", pageRequest);

        // then
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(User::getLoginId).containsExactlyInAnyOrder("testuser1", "testuser2");
    }

    @Test
    @DisplayName("findReportTargetMetadataByUserIds returns user metadata")
    void findReportTargetMetadataByUserIds_returnsUserMetadata() {
        List<UserRepository.ReportTargetMetadataProjection> metadata =
                userRepository.findReportTargetMetadataByUserIds(List.of(user1.getUserId()));

        assertThat(metadata).singleElement().satisfies(item -> {
            assertThat(item.getTargetId()).isEqualTo(user1.getUserId());
            assertThat(item.getTargetUserId()).isEqualTo(user1.getUserId());
            assertThat(item.getTargetDisplayName()).isEqualTo("Apple User");
            assertThat(item.getTargetLoginId()).isEqualTo("testuser1");
        });
    }

    @Test
    @DisplayName("사용자 검색 성공 - 닉네임으로 검색")
    void searchUsers_byDisplayName() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<User> result = userRepository.searchUsers("Apple", pageRequest);

        // then
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(User::getDisplayName).containsExactlyInAnyOrder("Apple User", "Apple Another");
    }

    @Test
    @DisplayName("사용자 검색 - 결과 없음")
    void searchUsers_noResult() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<User> result = userRepository.searchUsers("nonexistent", pageRequest);

        // then
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("공개 사용자 검색은 ACTIVE 이고 삭제되지 않은 사용자만 노출한다")
    void searchUsersVisibleTo_filtersSuspendedAndDeletedUsers() {
        User suspendedUser = User.builder()
                .loginId("suspended")
                .displayName("Apple Suspended")
                .email("suspended@test.com")
                .password("pass")
                .build();
        suspendedUser.suspend();

        User deletedUser = User.builder()
                .loginId("deleted")
                .displayName("Apple Deleted")
                .email("deleted@test.com")
                .password("pass")
                .build();
        deletedUser.delete(java.time.LocalDateTime.of(2026, 7, 7, 12, 0));

        entityManager.persist(suspendedUser);
        entityManager.persist(deletedUser);
        entityManager.flush();

        Page<User> result = userRepository.searchUsersVisibleTo("Apple", null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(User::getDisplayName)
                .containsExactlyInAnyOrder("Apple User", "Apple Another");
    }

    @Test
    @DisplayName("공개 사용자 검색은 차단 사용자도 제외한다")
    void searchUsersVisibleTo_excludesBlockedUsers() {
        Page<User> result = userRepository.searchUsersVisibleTo("Apple", List.of(user1.getUserId()), PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(User::getDisplayName)
                .containsExactly(user3.getDisplayName());
    }

    @Test
    @DisplayName("관리자 사용자 검색은 minActivityCount가 있을 때 활동량 집계 경로로 정렬과 total을 유지한다")
    void searchUsersForAdmin_withMinActivityCount_preservesOrderAndTotal() {
        persistActivitySearchFixture();

        UserAdminSearchCondition condition = UserAdminSearchCondition.builder()
                .minActivityCount(2L)
                .build();

        Page<User> result = userRepository.searchUsersForAdmin(
                null,
                condition,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "displayName")));

        assertThat(result.getTotalElements()).isEqualTo(2L);
        assertThat(result.getContent())
                .extracting(User::getDisplayName)
                .containsExactly("Apple Another", "Apple User");
    }

    @Test
    @DisplayName("관리자 사용자 검색은 활동량 집계 경로의 다음 페이지에서도 total을 유지한다")
    void searchUsersForAdmin_withMinActivityCount_preservesTotalOnSecondPage() {
        persistActivitySearchFixture();

        UserAdminSearchCondition condition = UserAdminSearchCondition.builder()
                .minActivityCount(2L)
                .build();

        Page<User> result = userRepository.searchUsersForAdmin(
                null,
                condition,
                PageRequest.of(1, 1, Sort.by(Sort.Direction.ASC, "displayName")));

        assertThat(result.getTotalElements()).isEqualTo(2L);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(User::getDisplayName)
                .containsExactly("Apple User");
    }

    @Test
    @DisplayName("관리자 사용자 검색은 활동량 집계 경로의 범위 밖 페이지에서도 total을 유지한다")
    void searchUsersForAdmin_withMinActivityCount_preservesTotalOnOutOfRangePage() {
        persistActivitySearchFixture();

        UserAdminSearchCondition condition = UserAdminSearchCondition.builder()
                .minActivityCount(2L)
                .build();

        Page<User> result = userRepository.searchUsersForAdmin(
                null,
                condition,
                PageRequest.of(99, 1, Sort.by(Sort.Direction.ASC, "displayName")));

        assertThat(result.getTotalElements()).isEqualTo(2L);
        assertThat(result.getContent()).isEmpty();
    }

    private void persistActivitySearchFixture() {
        Post user1Post1 = Post.builder().board(board).user(user1).title("p1").contents("c").build();
        Post user1Post2 = Post.builder().board(board).user(user1).title("p2").contents("c").build();
        entityManager.persist(user1Post1);
        entityManager.persist(user1Post2);
        entityManager.persist(Comment.builder().post(user1Post1).user(user1).content("comment").depth(0).build());

        Post user3Post = Post.builder().board(board).user(user3).title("u3").contents("c").build();
        entityManager.persist(user3Post);
        entityManager.persist(Comment.builder().post(user3Post).user(user3).content("comment").depth(0).build());
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("관리자 사용자 검색은 minActivityCount 경로에서도 role 필터 의미를 유지한다")
    void searchUsersForAdmin_withMinActivityCount_preservesRoleFilter() {
        Post user1Post1 = Post.builder().board(board).user(user1).title("p1").contents("c").build();
        Post user1Post2 = Post.builder().board(board).user(user1).title("p2").contents("c").build();
        entityManager.persist(user1Post1);
        entityManager.persist(user1Post2);

        Post user3Post1 = Post.builder().board(board).user(user3).title("u31").contents("c").build();
        Post user3Post2 = Post.builder().board(board).user(user3).title("u32").contents("c").build();
        entityManager.persist(user3Post1);
        entityManager.persist(user3Post2);
        entityManager.persist(Admin.builder().user(user3).board(board).role(Role.BOARD_ADMIN).build());
        entityManager.flush();
        entityManager.clear();

        UserAdminSearchCondition condition = UserAdminSearchCondition.builder()
                .minActivityCount(2L)
                .role(Role.USER)
                .build();

        Page<User> result = userRepository.searchUsersForAdmin(
                null,
                condition,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "displayName")));

        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getContent())
                .extracting(User::getDisplayName)
                .containsExactly("Apple User");
    }

    @Test
    @DisplayName("관리자 사용자 검색은 활동량 집계 경로와 기본 경로의 상태/탈퇴/날짜 필터 의미를 맞춘다")
    void searchUsersForAdmin_activityPathMatchesQuerydslForStatusWithdrawnAndDateFilters() {
        LocalDateTime base = LocalDateTime.of(2026, 4, 29, 0, 0);
        ReflectionTestUtils.setField(user1, "lastLoginAt", base.plusHours(1));
        ReflectionTestUtils.setField(user3, "lastLoginAt", base.plusHours(2));

        User suspended = User.builder()
                .loginId("suspended-filter")
                .displayName("Suspended Filter")
                .email("suspended-filter@test.com")
                .password("pass")
                .build();
        suspended.suspend();
        ReflectionTestUtils.setField(suspended, "lastLoginAt", base.plusHours(3));

        User deleted = User.builder()
                .loginId("deleted-filter")
                .displayName("Deleted Filter")
                .email("deleted-filter@test.com")
                .password("pass")
                .build();
        ReflectionTestUtils.setField(deleted, "lastLoginAt", base.plusHours(4));
        deleted.delete(java.time.LocalDateTime.of(2026, 7, 7, 12, 0));

        entityManager.persist(suspended);
        entityManager.persist(deleted);
        entityManager.flush();
        entityManager.clear();

        UserAdminSearchCondition querydslCondition = UserAdminSearchCondition.builder()
                .status(User.STATUS_ACTIVE)
                .isWithdrawn(false)
                .lastLoginFrom(base)
                .lastLoginTo(base.plusDays(1))
                .build();
        UserAdminSearchCondition nativeCondition = UserAdminSearchCondition.builder()
                .status(User.STATUS_ACTIVE)
                .isWithdrawn(false)
                .lastLoginFrom(base)
                .lastLoginTo(base.plusDays(1))
                .minActivityCount(0L)
                .build();

        Page<User> querydslResult = userRepository.searchUsersForAdmin(
                null,
                querydslCondition,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "displayName")));
        Page<User> nativeResult = userRepository.searchUsersForAdmin(
                null,
                nativeCondition,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "displayName")));

        assertSameOrderedUsers(nativeResult, querydslResult);
        assertThat(nativeResult.getContent()).extracting(User::getLoginId)
                .containsExactly("another", "testuser1");
    }

    @Test
    @DisplayName("관리자 사용자 검색은 활동량 집계 경로와 기본 경로의 역할 필터 의미를 맞춘다")
    void searchUsersForAdmin_activityPathMatchesQuerydslForRoleFilters() {
        User superAdmin = User.builder()
                .loginId("super-filter")
                .displayName("Super Filter")
                .email("super-filter@test.com")
                .password("pass")
                .build();
        superAdmin.grantSuperAdminRole();

        entityManager.persist(superAdmin);
        entityManager.persist(Admin.builder().user(user3).board(board).role("MODERATOR").build());
        entityManager.flush();
        entityManager.clear();

        UserAdminSearchCondition querydslCondition = UserAdminSearchCondition.builder()
                .role(" moderator ")
                .build();
        UserAdminSearchCondition nativeCondition = UserAdminSearchCondition.builder()
                .role(" moderator ")
                .minActivityCount(0L)
                .build();

        Page<User> querydslResult = userRepository.searchUsersForAdmin(
                null,
                querydslCondition,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "displayName")));
        Page<User> nativeResult = userRepository.searchUsersForAdmin(
                null,
                nativeCondition,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "displayName")));

        assertSameOrderedUsers(nativeResult, querydslResult);
        assertThat(nativeResult.getContent()).extracting(User::getLoginId)
                .containsExactly("another");
    }

    @Test
    @DisplayName("관리자 사용자 검색은 지원하지 않는 role 필터를 방어적으로 거절한다")
    void searchUsersForAdmin_rejectsUnsupportedRoleFilterDefensively() {
        UserAdminSearchCondition querydslCondition = UserAdminSearchCondition.builder()
                .role("UNKNOWN")
                .build();
        UserAdminSearchCondition nativeCondition = UserAdminSearchCondition.builder()
                .role("UNKNOWN")
                .minActivityCount(0L)
                .build();
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "unknown"));

        assertThatThrownBy(() -> userRepository.searchUsersForAdmin(null, querydslCondition, pageable))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unsupported role filter")
                .hasStackTraceContaining("IllegalArgumentException: Unsupported role filter");
        assertThatThrownBy(() -> userRepository.searchUsersForAdmin(null, nativeCondition, pageable))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unsupported role filter")
                .hasStackTraceContaining("IllegalArgumentException: Unsupported role filter");
    }

    @Test
    @DisplayName("관리자 대시보드 사용자 집계는 ACTIVE 이고 삭제되지 않은 사용자만 포함한다")
    void countActiveUsersForAdminDashboard_filtersSuspendedAndDeletedUsers() {
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        long activeUsersBefore = userRepository.countActiveUsersForAdminDashboard();
        long recentlyLoggedInBefore = userRepository.countRecentlyLoggedInActiveUsersForAdminDashboard(since);
        UserRepository.AdminDashboardUserStatsProjection dashboardStatsBefore =
                userRepository.countAdminDashboardUserStats(since);
        long publicLandingRecentlyLoggedInBefore =
                userRepository.countRecentlyLoggedInActiveUsersForPublicLanding(since);

        User recentActive = User.builder()
                .loginId("recent-active")
                .displayName("Recent Active")
                .email("recent-active@test.com")
                .password("pass")
                .build();
        ReflectionTestUtils.setField(recentActive, "lastLoginAt", since.plusHours(1));

        User oldActive = User.builder()
                .loginId("old-active")
                .displayName("Old Active")
                .email("old-active@test.com")
                .password("pass")
                .build();
        ReflectionTestUtils.setField(oldActive, "lastLoginAt", since.minusHours(1));

        User suspendedRecent = User.builder()
                .loginId("suspended-recent")
                .displayName("Suspended Recent")
                .email("suspended-recent@test.com")
                .password("pass")
                .build();
        suspendedRecent.suspend();
        ReflectionTestUtils.setField(suspendedRecent, "lastLoginAt", since.plusHours(2));

        User deletedRecent = User.builder()
                .loginId("deleted-recent")
                .displayName("Deleted Recent")
                .email("deleted-recent@test.com")
                .password("pass")
                .build();
        ReflectionTestUtils.setField(deletedRecent, "lastLoginAt", since.plusHours(3));
        deletedRecent.delete(java.time.LocalDateTime.of(2026, 7, 7, 12, 0));

        entityManager.persist(recentActive);
        entityManager.persist(oldActive);
        entityManager.persist(suspendedRecent);
        entityManager.persist(deletedRecent);
        entityManager.flush();

        assertThat(userRepository.countActiveUsersForAdminDashboard()).isEqualTo(activeUsersBefore + 2);
        assertThat(userRepository.countRecentlyLoggedInActiveUsersForAdminDashboard(since))
                .isEqualTo(recentlyLoggedInBefore + 1);
        UserRepository.AdminDashboardUserStatsProjection dashboardStats =
                userRepository.countAdminDashboardUserStats(since);
        assertThat(dashboardStats.getTotalUsers()).isEqualTo(dashboardStatsBefore.getTotalUsers() + 2);
        assertThat(dashboardStats.getActiveUsers()).isEqualTo(dashboardStatsBefore.getActiveUsers() + 1);
        assertThat(userRepository.countRecentlyLoggedInActiveUsersForPublicLanding(since))
                .isEqualTo(publicLandingRecentlyLoggedInBefore + 1);
    }

    @Test
    @DisplayName("공개 랜딩 사용자 통계는 active non-deleted 사용자만 한 번에 집계한다")
    void countPublicLandingUserStats_aggregatesActiveNonDeletedCounts() {
        LocalDateTime since = LocalDateTime.of(2099, 5, 4, 12, 0);

        User boundaryActive = User.builder()
                .loginId("landing-boundary-active")
                .displayName("Landing Boundary Active")
                .email("landing-boundary-active@test.com")
                .password("pass")
                .build();
        ReflectionTestUtils.setField(boundaryActive, "lastLoginAt", since);

        User recentActive = User.builder()
                .loginId("landing-recent-active")
                .displayName("Landing Recent Active")
                .email("landing-recent-active@test.com")
                .password("pass")
                .build();
        ReflectionTestUtils.setField(recentActive, "lastLoginAt", since.plusMinutes(2));

        User oldOnlineActive = User.builder()
                .loginId("landing-old-online")
                .displayName("Landing Old Online")
                .email("landing-old-online@test.com")
                .password("pass")
                .build();
        ReflectionTestUtils.setField(oldOnlineActive, "lastLoginAt", since.plusMinutes(3));

        User suspendedRecent = User.builder()
                .loginId("landing-suspended-recent")
                .displayName("Landing Suspended Recent")
                .email("landing-suspended-recent@test.com")
                .password("pass")
                .build();
        suspendedRecent.suspend();
        ReflectionTestUtils.setField(suspendedRecent, "lastLoginAt", since.plusMinutes(5));

        User deletedRecent = User.builder()
                .loginId("landing-deleted-recent")
                .displayName("Landing Deleted Recent")
                .email("landing-deleted-recent@test.com")
                .password("pass")
                .build();
        ReflectionTestUtils.setField(deletedRecent, "lastLoginAt", since.plusMinutes(7));
        deletedRecent.delete(java.time.LocalDateTime.of(2026, 7, 7, 12, 0));

        entityManager.persist(boundaryActive);
        entityManager.persist(recentActive);
        entityManager.persist(oldOnlineActive);
        entityManager.persist(suspendedRecent);
        entityManager.persist(deletedRecent);
        entityManager.flush();
        updateCreatedAt(boundaryActive, since);
        updateCreatedAt(recentActive, since.plusMinutes(1));
        updateCreatedAt(oldOnlineActive, since.minusDays(2));
        updateCreatedAt(suspendedRecent, since.plusMinutes(4));
        updateCreatedAt(deletedRecent, since.plusMinutes(6));
        entityManager.flush();
        entityManager.clear();

        UserRepository.PublicLandingUserStatsProjection stats = userRepository.countPublicLandingUserStats(since);

        assertThat(stats.getNewMembersLast24Hours()).isEqualTo(1L);
        assertThat(stats.getOnlineCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("관리자 사용자 상세 통계를 한 번에 집계한다")
    void countAdminUserDetailStats_aggregatesDetailCounts() {
        Post activePost = Post.builder().board(board).user(user1).title("active").contents("content").build();
        Post deletedPost = Post.builder().board(board).user(user1).title("deleted").contents("content").build();
        deletedPost.deletePost();
        entityManager.persist(activePost);
        entityManager.persist(deletedPost);
        Comment activeComment = Comment.builder().post(activePost).user(user1).content("active").depth(0).build();
        Comment deletedComment = Comment.builder().post(activePost).user(user1).content("deleted").depth(0).build();
        deletedComment.deleteComment();
        entityManager.persist(activeComment);
        entityManager.persist(deletedComment);
        entityManager.persist(BoardSubscription.builder()
                .user(user1)
                .board(board)
                .role("MEMBER")
                .sortOrder(1)
                .build());
        Admin admin = Admin.builder().user(user3).board(board).role(Role.BOARD_ADMIN).build();
        entityManager.persist(admin);
        entityManager.persist(Sanction.builder()
                .targetUser(user1)
                .admin(admin)
                .processorUser(admin.getUser())
                .type("BAN")
                .remark("target")
                .startDate(LocalDateTime.of(2026, 5, 4, 12, 0))
                .build());
        entityManager.persist(Sanction.builder()
                .targetUser(user3)
                .admin(admin)
                .processorUser(admin.getUser())
                .type("BAN")
                .remark("other")
                .startDate(LocalDateTime.of(2026, 5, 4, 13, 0))
                .build());
        User reporter = User.builder()
                .loginId("detail-reporter")
                .displayName("Detail Reporter")
                .email("detail-reporter@test.com")
                .password("pass")
                .build();
        entityManager.persist(reporter);
        entityManager.persist(Report.builder()
                .reporter(user3)
                .targetType(ReportTargetType.USER.name())
                .targetId(user1.getUserId())
                .reasonType("SPAM")
                .contents("pending")
                .build());
        Report resolvedReport = Report.builder()
                .reporter(reporter)
                .targetType(ReportTargetType.USER.name())
                .targetId(user1.getUserId())
                .reasonType("ABUSE")
                .contents("resolved")
                .build();
        resolvedReport.processReport(admin, user3.getUserId(), Report.STATUS_RESOLVED, "processed");
        entityManager.persist(resolvedReport);
        entityManager.persist(Report.builder()
                .reporter(user1)
                .targetType(ReportTargetType.USER.name())
                .targetId(user3.getUserId())
                .reasonType("SPAM")
                .contents("other target")
                .build());
        entityManager.flush();
        entityManager.clear();

        UserRepository.AdminUserDetailStatsProjection stats = userRepository.countAdminUserDetailStats(
                user1,
                ReportTargetType.USER.name(),
                Report.STATUS_PENDING);

        assertThat(stats.getPostCount()).isEqualTo(1L);
        assertThat(stats.getCommentCount()).isEqualTo(1L);
        assertThat(stats.getSubscriptionCount()).isEqualTo(1L);
        assertThat(stats.getSanctionCount()).isEqualTo(1L);
        assertThat(stats.getReportTotalCount()).isEqualTo(2L);
        assertThat(stats.getReportPendingCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("usable super admin query returns only active non-deleted super admins")
    void findUsableSuperAdmins_returnsOnlyUsableSuperAdmins() {
        User usable = User.builder()
                .loginId("usable-super")
                .displayName("Usable Super")
                .email("usable-super@test.com")
                .password("pass")
                .build();
        usable.grantSuperAdminRole();

        User suspended = User.builder()
                .loginId("suspended-super")
                .displayName("Suspended Super")
                .email("suspended-super@test.com")
                .password("pass")
                .build();
        suspended.grantSuperAdminRole();
        suspended.suspend();

        User deleted = User.builder()
                .loginId("deleted-super")
                .displayName("Deleted Super")
                .email("deleted-super@test.com")
                .password("pass")
                .build();
        deleted.grantSuperAdminRole();
        deleted.delete(java.time.LocalDateTime.of(2026, 7, 7, 12, 0));

        entityManager.persist(usable);
        entityManager.persist(suspended);
        entityManager.persist(deleted);
        entityManager.flush();
        entityManager.clear();

        List<User> result = userRepository.findUsableSuperAdmins();

        assertThat(result).extracting(User::getLoginId).containsExactly("usable-super");
    }

    @Test
    @DisplayName("usable super admin for-update query applies the same active filters")
    void findUsableSuperAdminsForUpdate_returnsOnlyUsableSuperAdmins() {
        User usable = User.builder()
                .loginId("usable-super-lock")
                .displayName("Usable Super Lock")
                .email("usable-super-lock@test.com")
                .password("pass")
                .build();
        usable.grantSuperAdminRole();

        User suspended = User.builder()
                .loginId("suspended-super-lock")
                .displayName("Suspended Super Lock")
                .email("suspended-super-lock@test.com")
                .password("pass")
                .build();
        suspended.grantSuperAdminRole();
        suspended.suspend();

        entityManager.persist(usable);
        entityManager.persist(suspended);
        entityManager.flush();
        entityManager.clear();

        List<User> result = userRepository.findUsableSuperAdminsForUpdate();

        assertThat(result).extracting(User::getLoginId).containsExactly("usable-super-lock");
    }

    @Test
    void findByIdForUpdate_declaresWriteLock() throws NoSuchMethodException {
        Method method = UserRepository.class.getMethod("findByIdForUpdate", Long.class);

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void findByEmailForUpdate_declaresWriteLock() throws NoSuchMethodException {
        Method method = UserRepository.class.getMethod("findByEmailForUpdate", String.class);

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    private void assertSameOrderedUsers(Page<User> actual, Page<User> expected) {
        assertThat(actual.getTotalElements()).isEqualTo(expected.getTotalElements());
        assertThat(actual.getContent()).extracting(User::getUserId)
                .containsExactlyElementsOf(expected.getContent().stream().map(User::getUserId).toList());
    }

    private void updateCreatedAt(User targetUser, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE users SET created_at = :createdAt WHERE user_id = :userId")
                .setParameter("createdAt", createdAt)
                .setParameter("userId", targetUser.getUserId())
                .executeUpdate();
    }
}
