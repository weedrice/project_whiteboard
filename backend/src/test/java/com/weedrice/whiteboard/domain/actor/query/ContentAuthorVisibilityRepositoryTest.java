package com.weedrice.whiteboard.domain.actor.query;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostSeries;
import com.weedrice.whiteboard.domain.post.entity.PostSeriesItem;
import com.weedrice.whiteboard.domain.post.repository.PostSeriesItemRepository;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({QuerydslConfig.class, ContentAuthorVisibilityRepository.class})
class ContentAuthorVisibilityRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ContentAuthorVisibilityRepository repository;

    @Autowired private BoardRepository boards;
    @Autowired private PostSeriesItemRepository seriesItems;

    @Test
    void boardRankingAndSeriesExcludeBannedAuthorsAndReopenAfterExpiry() {
        User active = persistUser("ranking-active");
        User banned = persistUser("ranking-banned");
        User muted = persistUser("ranking-muted");
        LocalDateTime now = LocalDateTime.now();
        sanction(active, banned, "BAN", now.minusDays(1), now.plusDays(1));
        sanction(active, muted, "MUTE", now.minusDays(1), null);
        Board visibleBoard = entityManager.persist(Board.builder().creator(active)
                .boardName("Visible").boardUrl("visibility-ranking").build());
        Board hiddenBoard = entityManager.persist(Board.builder().creator(active)
                .boardName("Hidden authors").boardUrl("hidden-ranking").build());
        Post visible = persistPost(visibleBoard, active);
        Post mutePost = persistPost(visibleBoard, muted);
        Post hidden = persistPost(hiddenBoard, banned);
        persistPost(hiddenBoard, banned);
        persistPost(hiddenBoard, banned);
        PostSeries series = entityManager.persist(PostSeries.builder().owner(active).title("Series").build());
        entityManager.persist(PostSeriesItem.builder().series(series).post(visible).sortOrder(0).build());
        entityManager.persist(PostSeriesItem.builder().series(series).post(hidden).sortOrder(1).build());
        entityManager.persist(PostSeriesItem.builder().series(series).post(mutePost).sortOrder(2).build());
        entityManager.flush();
        entityManager.clear();

        var limit = PageRequest.of(0, 1);
        assertThat(boards.findTopPublicBoardPostCounts("inquiry", limit))
                .extracting(BoardRepository.TopBoardPostCountProjection::getBoardId)
                .containsExactly(visibleBoard.getBoardId());
        assertThat(boards.findTopReadableBoardPostCounts(active, false, "inquiry", limit))
                .extracting(BoardRepository.TopBoardPostCountProjection::getPostCount).containsExactly(2L);
        assertThat(boards.findTopBoardIdsByPostCount(limit)).containsExactly(visibleBoard.getBoardId());
        assertThat(seriesItems.findBySeries_SeriesIdOrderBySortOrderAscItemIdAsc(series.getSeriesId()))
                .extracting(item -> item.getPost().getPostId())
                .containsExactly(visible.getPostId(), mutePost.getPostId());

        entityManager.getEntityManager().createQuery(
                        "UPDATE Sanction s SET s.endDate = :end WHERE s.targetUser.userId = :userId")
                .setParameter("end", now.minusMinutes(1)).setParameter("userId", banned.getUserId())
                .executeUpdate();
        entityManager.clear();
        assertThat(boards.findTopPublicBoardPostCounts("inquiry", limit))
                .extracting(BoardRepository.TopBoardPostCountProjection::getBoardId)
                .containsExactly(hiddenBoard.getBoardId());
        assertThat(seriesItems.findBySeries_SeriesIdOrderBySortOrderAscItemIdAsc(series.getSeriesId()))
                .extracting(item -> item.getPost().getPostId())
                .containsExactly(visible.getPostId(), hidden.getPostId(), mutePost.getPostId());
    }

    private Post persistPost(Board board, User author) {
        return entityManager.persist(Post.builder().board(board).user(author)
                .title("Visibility").contents("content").build());
    }

    @Test
    void findVisibleUserIds_filtersAndDeduplicatesCandidatesAndReopensAfterBanExpires() {
        LocalDateTime now = LocalDateTime.now();
        User active = persistUser("active");
        User banned = persistUser("banned");
        User muted = persistUser("muted");
        User future = persistUser("future");
        User expired = persistUser("expired");
        User inactive = persistUser("inactive");
        User deleted = persistUser("deleted");
        inactive.suspend();
        deleted.delete(now.minusDays(1));
        sanction(active, banned, "BAN", now.minusDays(1), null);
        sanction(active, banned, "ban", now.minusDays(1), now.plusDays(1));
        sanction(active, muted, "MUTE", now.minusDays(1), null);
        sanction(active, future, "BAN", now.plusDays(1), null);
        sanction(active, expired, "BAN", now.minusDays(2), now.minusDays(1));
        entityManager.flush();
        entityManager.clear();
        List<Long> candidates = Arrays.asList(active.getUserId(), active.getUserId(), banned.getUserId(),
                muted.getUserId(), future.getUserId(), expired.getUserId(), inactive.getUserId(),
                deleted.getUserId(), -1L, null);

        assertThat(repository.findVisibleUserIds(candidates)).containsExactlyInAnyOrder(
                active.getUserId(), muted.getUserId(), future.getUserId(), expired.getUserId(), deleted.getUserId());
        assertThat(repository.findVisibleUserIds(List.of(muted.getUserId()))).containsExactly(muted.getUserId());

        // Withdrawal does not override an effective BAN.
        sanction(entityManager.find(User.class, active.getUserId()),
                entityManager.find(User.class, deleted.getUserId()), "BAN", now.minusDays(1), null);
        entityManager.flush();
        assertThat(repository.findVisibleUserIds(List.of(deleted.getUserId()))).isEmpty();
        entityManager.getEntityManager().createQuery("DELETE FROM Sanction s WHERE s.targetUser.userId = :id")
                .setParameter("id", deleted.getUserId()).executeUpdate();
        entityManager.clear();

        entityManager.getEntityManager().createQuery(
                        "UPDATE Sanction s SET s.endDate = :end WHERE s.targetUser.userId = :userId")
                .setParameter("end", now.minusMinutes(1))
                .setParameter("userId", banned.getUserId())
                .executeUpdate();
        entityManager.clear();

        assertThat(repository.findVisibleUserIds(candidates)).containsExactlyInAnyOrder(
                active.getUserId(), banned.getUserId(), muted.getUserId(), future.getUserId(), expired.getUserId(), deleted.getUserId());
    }

    @Test
    void findVisibleUserIds_returnsEmptyForNoCandidates() {
        assertThat(repository.findVisibleUserIds(null)).isEmpty();
        assertThat(repository.findVisibleUserIds(List.of())).isEmpty();
        assertThat(repository.findVisibleUserIds(Arrays.asList(null, null))).isEmpty();
    }

    private User persistUser(String loginId) {
        return entityManager.persist(User.builder().loginId(loginId).email(loginId + "@visibility.test")
                .password("password").displayName(loginId).build());
    }

    private void sanction(User processor, User target, String type, LocalDateTime startsAt, LocalDateTime endsAt) {
        entityManager.persist(Sanction.builder().processorUser(processor).targetUser(target).type(type)
                .startDate(startsAt).endDate(endsAt).build());
    }
}
