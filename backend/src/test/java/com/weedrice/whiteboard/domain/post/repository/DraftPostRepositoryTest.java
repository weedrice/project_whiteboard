package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostSeries;
import com.weedrice.whiteboard.domain.post.entity.PostSeriesItem;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class DraftPostRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private DraftPostRepository draftPostRepository;

    @Autowired
    private PostSeriesRepository postSeriesRepository;

    @Autowired
    private PostSeriesItemRepository postSeriesItemRepository;

    private User user;
    private Board board;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("draft-user")
                .email("draft@test.com")
                .password("password")
                .displayName("초안유저")
                .build();
        entityManager.persist(user);

        board = Board.builder()
                .boardName("draft-board")
                .boardUrl("draft-board")
                .creator(user)
                .build();
        entityManager.persist(board);

        entityManager.persist(DraftPost.builder()
                .user(user)
                .board(board)
                .title("draft")
                .contents("contents")
                .build());
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("초안 목록 전용 조회는 board를 함께 로드한다")
    void findPageByUserWithBoard_fetchesBoard() {
        Page<DraftPost> result = draftPostRepository.findPageByUserWithBoard(user, PageRequest.of(0, 10));
        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();

        assertThat(result.getContent()).hasSize(1);
        DraftPost draftPost = result.getContent().getFirst();
        assertThat(persistenceUnitUtil.isLoaded(draftPost, "board")).isTrue();
        assertThat(draftPost.getBoard().getBoardName()).isEqualTo("draft-board");
    }

    @Test
    @DisplayName("같은 수정 시각의 임시글은 draftId 내림차순으로 조회된다")
    void findPageByUserWithBoard_ordersByModifiedAtAndDraftIdDesc() {
        DraftPost olderDraftId = DraftPost.builder()
                .user(user)
                .board(board)
                .title("older-id")
                .contents("contents")
                .build();
        DraftPost newerDraftId = DraftPost.builder()
                .user(user)
                .board(board)
                .title("newer-id")
                .contents("contents")
                .build();
        entityManager.persist(olderDraftId);
        entityManager.persist(newerDraftId);
        entityManager.flush();

        LocalDateTime sameModifiedAt = LocalDateTime.of(2030, 1, 1, 0, 0);
        updateDraftModifiedAt(olderDraftId.getDraftId(), sameModifiedAt);
        updateDraftModifiedAt(newerDraftId.getDraftId(), sameModifiedAt);
        entityManager.clear();

        Page<DraftPost> result = draftPostRepository.findPageByUserWithBoard(user, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(DraftPost::getDraftId)
                .containsSubsequence(newerDraftId.getDraftId(), olderDraftId.getDraftId());
    }

    @Test
    @DisplayName("예약발행이 보호하는 초안은 일반 목록과 일반 초안 한도에서 제외된다")
    void findPageByUserWithBoard_excludesProtectedScheduledDrafts() {
        DraftPost protectedDraft = DraftPost.builder()
                .user(user)
                .board(board)
                .clientDraftKey("protected-client-key")
                .title("protected")
                .contents("contents")
                .build();
        entityManager.persist(protectedDraft);
        entityManager.flush();
        entityManager.persist(ScheduledPost.builder()
                .user(user)
                .board(board)
                .title("scheduled")
                .contents("contents")
                .draftId(protectedDraft.getDraftId())
                .scheduledAt(LocalDateTime.of(2030, 1, 1, 0, 0))
                .build());
        entityManager.flush();
        entityManager.clear();

        Page<DraftPost> result = draftPostRepository.findPageByUserWithBoard(user, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(DraftPost::getTitle)
                .containsExactly("draft");
        assertThat(draftPostRepository.countDeletableByUser(user)).isEqualTo(1);
        assertThat(draftPostRepository.findRecoverableByUserAndClientDraftKeyAndTarget(
                user, "protected-client-key", "draft-board", null)).isEmpty();
    }

    @Test
    @DisplayName("복구용 조회는 작성 대상에 맞는 최신 초안을 최대 요청 개수만 반환한다")
    void findMatchingByUserAndTarget_returnsStableLimitedCandidates() {
        entityManager.persist(DraftPost.builder()
                .user(user)
                .board(board)
                .title("newer")
                .contents("contents")
                .build());
        entityManager.persist(DraftPost.builder()
                .user(user)
                .board(board)
                .title("newest")
                .contents("contents")
                .build());
        entityManager.flush();
        entityManager.clear();

        List<DraftPost> result = draftPostRepository.findMatchingByUserAndTarget(
                user, "draft-board", null, PageRequest.of(0, 2));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DraftPost::getTitle).containsExactly("newest", "newer");
    }

    @Test
    @DisplayName("복구용 조회는 clientDraftKey와 작성 대상이 모두 일치하는 초안을 반환한다")
    void findRecoverableByUserAndClientDraftKeyAndTarget_returnsExactMatch() {
        DraftPost exact = DraftPost.builder()
                .user(user)
                .board(board)
                .clientDraftKey("client-draft-key-1234")
                .title("exact")
                .contents("contents")
                .build();
        entityManager.persist(exact);
        entityManager.flush();
        entityManager.clear();

        assertThat(draftPostRepository.findRecoverableByUserAndClientDraftKeyAndTarget(
                user, "client-draft-key-1234", "draft-board", null))
                .get()
                .extracting(DraftPost::getTitle)
                .isEqualTo("exact");
        assertThat(draftPostRepository.findRecoverableByUserAndClientDraftKeyAndTarget(
                user, "client-draft-key-1234", "other-board", null)).isEmpty();
    }

    @Test
    @DisplayName("시리즈 삭제 전 게시글 연결과 임시글 참조를 정리할 수 있다")
    void cleanupSeriesReferences_allowsSeriesDeletion() {
        PostSeries series = PostSeries.builder()
                .owner(user)
                .title("series")
                .build();
        entityManager.persist(series);
        Post post = Post.builder()
                .user(user)
                .board(board)
                .title("post")
                .contents("contents")
                .build();
        entityManager.persist(post);
        entityManager.persist(PostSeriesItem.builder()
                .series(series)
                .post(post)
                .sortOrder(0)
                .build());
        DraftPost linkedDraft = DraftPost.builder()
                .user(user)
                .board(board)
                .series(series)
                .title("linked")
                .contents("contents")
                .build();
        entityManager.persist(linkedDraft);
        entityManager.flush();

        assertThat(postSeriesItemRepository.deleteAllBySeriesId(series.getSeriesId())).isEqualTo(1);
        assertThat(draftPostRepository.clearSeriesReference(series.getSeriesId())).isEqualTo(1);
        postSeriesRepository.delete(series);
        entityManager.flush();
        entityManager.clear();

        assertThat(postSeriesRepository.findById(series.getSeriesId())).isEmpty();
        assertThat(postSeriesItemRepository.findByPost_PostId(post.getPostId())).isEmpty();
        assertThat(draftPostRepository.findById(linkedDraft.getDraftId()))
                .get()
                .extracting(DraftPost::getSeries)
                .isNull();
    }

    private void updateDraftModifiedAt(Long draftId, LocalDateTime modifiedAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE draft_posts SET modified_at = :modifiedAt WHERE draft_id = :draftId")
                .setParameter("modifiedAt", modifiedAt)
                .setParameter("draftId", draftId)
                .executeUpdate();
    }
}
