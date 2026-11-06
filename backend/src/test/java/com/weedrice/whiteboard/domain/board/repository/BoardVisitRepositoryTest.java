package com.weedrice.whiteboard.domain.board.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardVisit;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserBlock;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class BoardVisitRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BoardVisitRepository boardVisitRepository;

    @Test
    void activityExcludesHiddenAndBlockedAuthorsAndReturnsAfterBanExpiry() {
        User reader = persistUser("visibility-reader");
        User active = persistUser("visibility-active");
        User banned = persistUser("visibility-banned");
        User muted = persistUser("visibility-muted");
        User future = persistUser("visibility-future");
        User inactive = persistUser("visibility-inactive");
        User deleted = persistUser("visibility-deleted");
        User blocked = persistUser("visibility-blocked");
        User blocker = persistUser("visibility-blocker");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        inactive.suspend();
        deleted.delete(now.minusDays(1));
        entityManager.persist(Sanction.builder().targetUser(banned).processorUser(reader).type("BAN")
                .startDate(now.minusDays(1)).endDate(now.plusDays(1)).build());
        entityManager.persist(Sanction.builder().targetUser(muted).processorUser(reader).type("MUTE")
                .startDate(now.minusDays(1)).build());
        entityManager.persist(Sanction.builder().targetUser(future).processorUser(reader).type("BAN")
                .startDate(now.plusDays(1)).build());
        entityManager.persist(UserBlock.builder().user(reader).target(blocked).build());
        entityManager.persist(UserBlock.builder().user(blocker).target(reader).build());
        Board board = persistBoard("visibility-activity", active);
        Board hiddenOnlyBoard = persistBoard("hidden-activity", active);
        entityManager.persist(new BoardVisit(reader, board, now.minusDays(2)));
        for (User author : List.of(active, muted, future)) {
            setCreatedAt(persistPost(board, author, "visible"), now.minusDays(1));
        }
        for (User author : List.of(banned, inactive, deleted, blocked, blocker)) {
            setCreatedAt(persistPost(board, author, "hidden"), now.minusHours(1));
        }
        setCreatedAt(persistPost(hiddenOnlyBoard, banned, "only hidden"), now.minusHours(1));
        entityManager.flush();
        entityManager.clear();

        var result = boardVisitRepository.findActivityByBoardIds(
                List.of(board.getBoardId(), hiddenOnlyBoard.getBoardId()), reader.getUserId(), false,
                now.minusDays(30));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getNewPostCount()).isEqualTo(3);
        assertThat(result.getFirst().getLatestPostAt()).isEqualTo(now.minusDays(1));
        // Anonymous visitors have no personal block relationship, but still obey the shared policy.
        var anonymous = boardVisitRepository.findActivityByBoardIds(
                List.of(board.getBoardId()), null, false, now.minusDays(30));
        assertThat(anonymous.getFirst().getNewPostCount()).isEqualTo(5);

        entityManager.getEntityManager().createQuery(
                        "UPDATE Sanction s SET s.endDate = :end WHERE s.targetUser.userId = :id")
                .setParameter("end", now.minusMinutes(1)).setParameter("id", banned.getUserId()).executeUpdate();
        entityManager.clear();
        var reopened = boardVisitRepository.findActivityByBoardIds(
                List.of(hiddenOnlyBoard.getBoardId()), reader.getUserId(), false, now.minusDays(30));
        assertThat(reopened.getFirst().getNewPostCount()).isEqualTo(1);
        assertThat(reopened.getFirst().getLatestPostAt()).isEqualTo(now.minusHours(1));
    }

    @Test
    void findActivityByBoardIds_countsOnlyPostsAfterRecentCutoff() {
        User user = persistUser("reader");
        User writer = persistUser("writer");
        Board board = persistBoard("activity-board", writer);
        LocalDateTime now = LocalDateTime.of(2026, 7, 9, 12, 0);
        LocalDateTime recentCutoff = now.minusDays(30);
        entityManager.persist(new BoardVisit(user, board, now.minusDays(60)));

        Post excludedOldPost = persistPost(board, writer, "old");
        Post recentPost = persistPost(board, writer, "recent");
        Post blindedRecentPost = persistPost(board, writer, "blinded-recent");
        blindedRecentPost.blind("reported", now.minusDays(1));
        setCreatedAt(excludedOldPost, now.minusDays(35));
        setCreatedAt(recentPost, now.minusDays(5));
        setCreatedAt(blindedRecentPost, now.minusDays(1));
        entityManager.flush();
        entityManager.clear();

        List<BoardVisitRepository.BoardActivityProjection> result = boardVisitRepository.findActivityByBoardIds(
                List.of(board.getBoardId()),
                user.getUserId(),
                false,
                recentCutoff);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBoardId()).isEqualTo(board.getBoardId());
        assertThat(result.get(0).getNewPostCount()).isEqualTo(1L);
        assertThat(result.get(0).getLatestPostAt()).isEqualTo(now.minusDays(5));
    }

    @Test
    void upsert_insertsOnceAndUpdatesVisitTimestamp() {
        User user = persistUser("upsert-reader");
        User creator = persistUser("upsert-creator");
        Board board = persistBoard("upsert-board", creator);
        entityManager.flush();
        entityManager.clear();
        LocalDateTime firstVisit = LocalDateTime.of(2026, 7, 10, 10, 0);
        LocalDateTime secondVisit = firstVisit.plusMinutes(5);

        assertThat(boardVisitRepository.upsert(user.getUserId(), board.getBoardUrl(), firstVisit)).isEqualTo(1);
        assertThat(boardVisitRepository.upsert(user.getUserId(), board.getBoardUrl(), secondVisit)).isEqualTo(1);
        entityManager.clear();

        assertThat(boardVisitRepository.count()).isEqualTo(1);
        BoardVisit visit = boardVisitRepository.findById(
                new com.weedrice.whiteboard.domain.board.entity.BoardVisitId(
                        user.getUserId(), board.getBoardId())).orElseThrow();
        assertThat(visit.getLastVisitedAt()).isEqualTo(secondVisit);
    }

    @Test
    void upsert_doesNotMoveVisitTimestampBackwards() {
        User user = persistUser("monotonic-reader");
        User creator = persistUser("monotonic-creator");
        Board board = persistBoard("monotonic-board", creator);
        entityManager.flush();
        entityManager.clear();
        LocalDateTime latestVisit = LocalDateTime.of(2026, 7, 10, 10, 5);
        LocalDateTime staleVisit = latestVisit.minusMinutes(5);

        assertThat(boardVisitRepository.upsert(user.getUserId(), board.getBoardUrl(), latestVisit)).isEqualTo(1);
        assertThat(boardVisitRepository.upsert(user.getUserId(), board.getBoardUrl(), staleVisit)).isEqualTo(1);
        entityManager.clear();

        BoardVisit visit = boardVisitRepository.findById(
                new com.weedrice.whiteboard.domain.board.entity.BoardVisitId(
                        user.getUserId(), board.getBoardId())).orElseThrow();
        assertThat(visit.getLastVisitedAt()).isEqualTo(latestVisit);
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

    private Board persistBoard(String boardUrl, User creator) {
        Board board = Board.builder()
                .boardName(boardUrl)
                .boardUrl(boardUrl)
                .creator(creator)
                .isPublic(true)
                .build();
        entityManager.persist(board);
        return board;
    }

    private Post persistPost(Board board, User writer, String title) {
        Post post = Post.builder()
                .board(board)
                .user(writer)
                .title(title)
                .contents("contents")
                .isNotice(false)
                .isNsfw(false)
                .isSpoiler(false)
                .isSecret(false)
                .build();
        entityManager.persist(post);
        entityManager.flush();
        return post;
    }

    private void setCreatedAt(Post post, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE posts SET created_at = :createdAt, modified_at = :createdAt WHERE post_id = :postId")
                .setParameter("createdAt", createdAt)
                .setParameter("postId", post.getPostId())
                .executeUpdate();
    }
}
