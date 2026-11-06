package com.weedrice.whiteboard.domain.feed.repository;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class UserFeedRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserFeedRepository userFeedRepository;

    private User viewer;
    private User author;
    private Board publicBoard;

    @BeforeEach
    void setUp() {
        viewer = persistUser("viewer");
        author = persistUser("author");
        publicBoard = persistBoard("public-board", author, true, true);
    }

    @Test
    void findVisibleByTargetUserOrderByCreatedAtDesc_excludesInvisiblePostFeedsAndKeepsMetadata() {
        Post visiblePost = persistPost(publicBoard, author, false);
        Post deletedPost = persistPost(publicBoard, author, false);
        deletedPost.deletePost();
        Post blindedPost = persistPost(publicBoard, viewer, false);
        blindedPost.blind("AUTO_REPORT", LocalDateTime.now());
        Post blockedAuthorPost = persistPost(publicBoard, author, false);

        persistFeed(viewer, "POST", visiblePost.getPostId());
        persistFeed(viewer, "POST", deletedPost.getPostId());
        persistFeed(viewer, "POST", blindedPost.getPostId());
        persistFeed(viewer, "POST", blockedAuthorPost.getPostId());
        persistFeed(viewer, "POST", 999_999L);
        persistFeed(viewer, "NOTICE", 10L);
        entityManager.flush();
        entityManager.clear();

        Page<UserFeed> result = userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(
                vf(viewer, List.of(author.getUserId()), List.of()),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent())
                .extracting(UserFeed::getContentType)
                .containsExactly("NOTICE");
    }

    @Test
    void findVisibleByTargetUserOrderByCreatedAtDesc_allowsSecretPostForAuthorOnly() {
        Post ownSecretPost = persistPost(publicBoard, viewer, true);
        Post otherSecretPost = persistPost(publicBoard, author, true);
        persistFeed(viewer, "POST", ownSecretPost.getPostId());
        persistFeed(viewer, "POST", otherSecretPost.getPostId());
        entityManager.flush();
        entityManager.clear();

        Page<UserFeed> result = userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(
                vf(viewer),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getContentId()).isEqualTo(ownSecretPost.getPostId());
    }

    @Test
    void findVisibleByTargetUserOrderByCreatedAtDesc_allowsPrivateBoardForPrecomputedAdminBoardId() {
        Board privateBoard = persistBoard("private-board", author, true, false);
        Post privatePost = persistPost(privateBoard, author, false);
        persistFeed(viewer, "POST", privatePost.getPostId());
        entityManager.flush();
        entityManager.clear();

        Page<UserFeed> result = userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(
                vf(viewer, List.of(), List.of(privateBoard.getBoardId())),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getContentId()).isEqualTo(privatePost.getPostId());
    }

    @Test
    void findVisibleByTargetUserOrderByCreatedAtDesc_usesPrecomputedAdminBoardIdsOnly() {
        Board privateBoard = persistBoard("private-board-hidden", author, true, false);
        entityManager.persist(Admin.builder()
                .user(viewer)
                .board(privateBoard)
                .role(Role.BOARD_ADMIN)
                .build());
        Post privatePost = persistPost(privateBoard, author, false);
        persistFeed(viewer, "POST", privatePost.getPostId());
        entityManager.flush();
        entityManager.clear();

        Page<UserFeed> result = userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(
                vf(viewer),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void findVisibleByTargetUserOrderByCreatedAtDesc_allowsPrivateBoardForSuperAdmin() {
        viewer.grantSuperAdminRole();
        Board privateBoard = persistBoard("private-board-super-admin", author, true, false);
        Post privatePost = persistPost(privateBoard, author, false);
        persistFeed(viewer, "POST", privatePost.getPostId());
        entityManager.flush();
        entityManager.clear();

        Page<UserFeed> result = userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(
                vf(viewer),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getContentId()).isEqualTo(privatePost.getPostId());
    }

    @Test
    void findVisibleByTargetUserOrderByCreatedAtDesc_allowsInactiveBoardForAuthorOnly() {
        Board inactiveBoard = persistBoard("inactive-author-board", author, false, true);
        Post ownPost = persistPost(inactiveBoard, viewer, false);
        Post otherPost = persistPost(inactiveBoard, author, false);
        persistFeed(viewer, "POST", ownPost.getPostId());
        persistFeed(viewer, "POST", otherPost.getPostId());
        entityManager.flush();
        entityManager.clear();

        Page<UserFeed> result = userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(
                vf(viewer),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getContentId()).isEqualTo(ownPost.getPostId());
    }

    @Test
    void findVisibleByTargetUserOrderByCreatedAtDesc_allowsInactiveSecretPostForPrecomputedAdminBoardId() {
        Board inactiveBoard = persistBoard("inactive-admin-board", author, false, true);
        Post secretPost = persistPost(inactiveBoard, author, true);
        persistFeed(viewer, "POST", secretPost.getPostId());
        entityManager.flush();
        entityManager.clear();

        Page<UserFeed> result = userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(
                vf(viewer, List.of(), List.of(inactiveBoard.getBoardId())),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getContentId()).isEqualTo(secretPost.getPostId());
    }

    @Test
    void findVisibleByTargetUserOrderByCreatedAtDesc_allowsPrivateInquiryBoardForAuthorOnly() {
        Board inquiryBoard = persistBoard(BoardPolicyConstants.INQUIRY_BOARD_URL, author, true, false);
        Post ownInquiryPost = persistPost(inquiryBoard, viewer, false);
        Post otherInquiryPost = persistPost(inquiryBoard, author, false);
        persistFeed(viewer, "POST", ownInquiryPost.getPostId());
        persistFeed(viewer, "POST", otherInquiryPost.getPostId());
        entityManager.flush();
        entityManager.clear();

        Page<UserFeed> result = userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(
                vf(viewer),
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().getContentId()).isEqualTo(ownInquiryPost.getPostId());
    }

    @Test
    void findVisibleByTargetUserOrderByCreatedAtDesc_excludesInquiryAfterLegacyCutover() {
        Board inquiryBoard = persistBoard(BoardPolicyConstants.INQUIRY_BOARD_URL, author, true, false);
        Post ownInquiryPost = persistPost(inquiryBoard, viewer, false);
        persistFeed(viewer, "POST", ownInquiryPost.getPostId());
        entityManager.flush();
        entityManager.clear();

        Page<UserFeed> result = userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(
                UserFeedVisibilityCondition.of(viewer, List.of(), List.of(), false),
                PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void findVisibleByTargetUserOrderByCreatedAtDesc_ordersByFeedIdWhenCreatedAtTies() {
        UserFeed first = persistFeed(viewer, "NOTICE", 10L);
        UserFeed second = persistFeed(viewer, "NOTICE", 20L);
        LocalDateTime sameCreatedAt = LocalDateTime.now().minusMinutes(1);
        entityManager.flush();
        updateCreatedAt(first, sameCreatedAt);
        updateCreatedAt(second, sameCreatedAt);
        entityManager.clear();

        Page<UserFeed> result = userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(
                vf(viewer),
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(UserFeed::getFeedId)
                .containsSubsequence(second.getFeedId(), first.getFeedId());
    }

    @Test
    void subscriptionPostFeedTargetFilter_excludesBannedInactiveAndDeletedSubscribers() throws NoSuchMethodException {
        User activeSubscriber = persistUser("active-subscriber");
        User moderatorSubscriber = persistUser("moderator-subscriber");
        User bannedSubscriber = persistUser("banned-subscriber");
        User suspendedSubscriber = persistUser("suspended-subscriber");
        User deletedSubscriber = persistUser("deleted-subscriber");
        suspendedSubscriber.suspend();
        deletedSubscriber.delete(java.time.LocalDateTime.of(2026, 7, 7, 12, 0));
        persistSubscription(activeSubscriber, publicBoard, "MEMBER");
        persistSubscription(moderatorSubscriber, publicBoard, "MODERATOR");
        persistSubscription(bannedSubscriber, publicBoard, "BANNED");
        persistSubscription(suspendedSubscriber, publicBoard, "MEMBER");
        persistSubscription(deletedSubscriber, publicBoard, "MEMBER");
        entityManager.flush();
        entityManager.clear();

        String targetSelectQuery = extractSubscriptionPostFeedTargetSelectQuery();
        java.util.stream.Stream<?> targetResults = entityManager.getEntityManager()
                .createNativeQuery(targetSelectQuery)
                .setParameter("boardId", publicBoard.getBoardId())
                .getResultStream();
        List<Long> targetUserIds = targetResults
                .map(result -> ((Number) result).longValue())
                .toList();

        assertThat(targetUserIds)
                .containsExactlyInAnyOrder(activeSubscriber.getUserId(), moderatorSubscriber.getUserId());
    }

    @Test
    void insertSubscriptionPostFeeds_queryKeepsEligibilityFiltersAndConflictIgnore() throws NoSuchMethodException {
        String query = getInsertSubscriptionPostFeedsQuery();

        assertThat(query)
                .contains("JOIN users u ON u.user_id = bs.user_id")
                .contains("bs.role <> 'BANNED'")
                .contains("u.status = 'ACTIVE'")
                .contains("u.deleted_at IS NULL")
                .contains("ON CONFLICT ON CONSTRAINT uk_user_feeds_target_content_source DO NOTHING");
    }

    @Test
    void deleteHardStalePostFeeds_deletesOnlyMissingOrDeletedPostFeeds() {
        Post activePost = persistPost(publicBoard, author, false);
        Post deletedPost = persistPost(publicBoard, author, false);
        deletedPost.deletePost();
        persistFeed(viewer, "POST", activePost.getPostId());
        persistFeed(viewer, "POST", deletedPost.getPostId());
        persistFeed(viewer, "POST", 999_999L);
        persistFeed(viewer, "NOTICE", 999_999L);
        entityManager.flush();
        entityManager.clear();

        int deletedCount = userFeedRepository.deleteHardStalePostFeeds("POST");
        entityManager.flush();
        entityManager.clear();

        assertThat(deletedCount).isEqualTo(2);
        assertThat(userFeedRepository.findAll())
                .extracting(UserFeed::getContentType, UserFeed::getContentId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("POST", activePost.getPostId()),
                        org.assertj.core.groups.Tuple.tuple("NOTICE", 999_999L));
    }

    @Test
    void visibleFeeds_filterAuthorEligibilityBeforePagingAndKeepSystemFeeds() {
        LocalDateTime now = LocalDateTime.now();
        User expired = persistUser("feed-expired");
        User future = persistUser("feed-future");
        User muted = persistUser("feed-muted");
        User banned = persistUser("feed-banned");
        User permanent = persistUser("feed-permanent");
        User inactive = persistUser("feed-inactive");
        User deleted = persistUser("feed-deleted");
        User blocked = persistUser("feed-blocked");
        inactive.suspend();
        deleted.delete(now.minusDays(1));
        persistBanOrMute(expired, "BAN", now.minusDays(2), now.minusDays(1));
        persistBanOrMute(future, "BAN", now.plusDays(1), null);
        persistBanOrMute(muted, "MUTE", now.minusDays(1), null);
        persistBanOrMute(banned, "BAN", now.minusDays(1), now.plusDays(1));
        persistBanOrMute(banned, "ban", now.minusDays(2), now.plusDays(2));
        persistBanOrMute(permanent, "BAN", now.minusDays(1), null);
        Post activePost = persistPost(publicBoard, author, false);
        Post expiredPost = persistPost(publicBoard, expired, false);
        Post futurePost = persistPost(publicBoard, future, false);
        Post mutedPost = persistPost(publicBoard, muted, false);
        Post withdrawnPost = persistPost(publicBoard, deleted, false);
        for (Post post : List.of(activePost, expiredPost, futurePost, mutedPost, withdrawnPost)) {
            persistFeed(viewer, "POST", post.getPostId());
        }
        UserFeed systemFeed = persistFeed(viewer, "NOTICE", 987L);
        // Hidden feeds are newer than every visible feed and must not consume the first page.
        for (User hidden : List.of(banned, permanent, inactive, blocked)) {
            Post hiddenPost = persistPost(publicBoard, hidden, false);
            persistFeed(viewer, "POST", hiddenPost.getPostId());
        }
        entityManager.flush();
        for (UserFeed feed : userFeedRepository.findAll()) {
            updateCreatedAt(feed, now);
        }
        entityManager.clear();
        UserFeedVisibilityCondition condition = vf(viewer, List.of(blocked.getUserId()), List.of());

        Page<UserFeed> first = userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(
                condition, PageRequest.of(0, 2));
        Page<UserFeed> second = userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(
                condition, PageRequest.of(1, 2));
        Page<UserFeed> third = userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(
                condition, PageRequest.of(2, 2));

        assertThat(first.getTotalElements()).isEqualTo(6);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.getContent()).extracting(UserFeed::getFeedId).startsWith(systemFeed.getFeedId());
        assertThat(first.getContent()).extracting(UserFeed::getContentId)
                .containsExactly(987L, withdrawnPost.getPostId());
        assertThat(second.getContent()).extracting(UserFeed::getContentId)
                .containsExactly(mutedPost.getPostId(), futurePost.getPostId());
        assertThat(third.getContent()).extracting(UserFeed::getContentId)
                .containsExactly(expiredPost.getPostId(), activePost.getPostId());
        assertThat(second.getTotalElements()).isEqualTo(6);
        assertThat(third.getTotalElements()).isEqualTo(6);
    }

    @Test
    void visibleFeeds_reappearAfterBanExpiresWithoutRegeneratingFeedRows() {
        Post target = persistPost(publicBoard, author, false);
        persistFeed(viewer, "POST", target.getPostId());
        persistBanOrMute(author, "BAN", LocalDateTime.now().minusDays(1), null);
        entityManager.flush();
        entityManager.clear();

        assertThat(userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(vf(viewer), PageRequest.of(0, 10))
                .getTotalElements()).isZero();
        assertThat(userFeedRepository.count()).isEqualTo(1);

        entityManager.getEntityManager().createQuery(
                        "UPDATE Sanction s SET s.endDate = :endedAt WHERE s.targetUser.userId = :authorId")
                .setParameter("endedAt", LocalDateTime.now().minusMinutes(1))
                .setParameter("authorId", author.getUserId())
                .executeUpdate();
        entityManager.clear();

        Page<UserFeed> result = userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(
                vf(viewer), PageRequest.of(0, 10));
        assertThat(result.getContent()).extracting(UserFeed::getContentId).containsExactly(target.getPostId());
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(userFeedRepository.count()).isEqualTo(1);
    }

    private void persistBanOrMute(User target, String type, LocalDateTime start, LocalDateTime end) {
        entityManager.persist(Sanction.builder().targetUser(target).processorUser(viewer)
                .type(type).startDate(start).endDate(end).build());
    }

    private String extractSubscriptionPostFeedTargetSelectQuery() throws NoSuchMethodException {
        String query = getInsertSubscriptionPostFeedsQuery();
        int fromStart = query.indexOf("FROM board_subscriptions");
        int conflictStart = query.indexOf("ON CONFLICT");
        return "SELECT DISTINCT bs.user_id " + query.substring(fromStart, conflictStart);
    }

    private String getInsertSubscriptionPostFeedsQuery() throws NoSuchMethodException {
        Method method = UserFeedRepository.class.getMethod(
                "insertSubscriptionPostFeeds",
                Long.class,
                String.class,
                String.class,
                Long.class,
                String.class,
                Long.class);
        return method.getAnnotation(Query.class).value();
    }

    private User persistUser(String loginId) {
        User user = User.builder()
                .loginId(loginId)
                .email(loginId + "@test.com")
                .password("password")
                .displayName(loginId)
                .build();
        entityManager.persist(user);
        return user;
    }

    private Board persistBoard(String boardUrl, User creator, boolean isActive, boolean isPublic) {
        Board board = Board.builder()
                .boardName(boardUrl)
                .boardUrl(boardUrl)
                .creator(creator)
                .isPublic(isPublic)
                .build();
        if (!isActive) {
            board.deactivate();
        }
        entityManager.persist(board);
        return board;
    }

    private Post persistPost(Board board, User author, boolean isSecret) {
        Post post = Post.builder()
                .board(board)
                .user(author)
                .title("title")
                .contents("contents")
                .isSecret(isSecret)
                .build();
        entityManager.persist(post);
        return post;
    }

    private void persistSubscription(User user, Board board, String role) {
        entityManager.persist(BoardSubscription.builder()
                .user(user)
                .board(board)
                .role(role)
                .sortOrder(1)
                .build());
    }

    private UserFeed persistFeed(User targetUser, String contentType, Long contentId) {
        UserFeed feed = UserFeed.builder()
                .targetUser(targetUser)
                .feedType("SUBSCRIPTION_POST")
                .contentType(contentType)
                .contentId(contentId)
                .sourceCriteria("BOARD_SUBSCRIPTION")
                .criteriaId(1L)
                .build();
        entityManager.persist(feed);
        return feed;
    }

    private void updateCreatedAt(UserFeed feed, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE user_feeds SET created_at = :createdAt WHERE feed_id = :feedId")
                .setParameter("createdAt", createdAt)
                .setParameter("feedId", feed.getFeedId())
                .executeUpdate();
    }

    private UserFeedVisibilityCondition vf(User targetUser) {
        return vf(targetUser, List.of(), List.of());
    }

    private UserFeedVisibilityCondition vf(User targetUser, List<Long> blockedUserIds, List<Long> activeAdminBoardIds) {
        return UserFeedVisibilityCondition.of(targetUser, blockedUserIds, activeAdminBoardIds, true);
    }
}
