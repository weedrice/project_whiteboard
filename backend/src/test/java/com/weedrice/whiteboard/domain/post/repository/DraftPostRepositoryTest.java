package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

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

    private void updateDraftModifiedAt(Long draftId, LocalDateTime modifiedAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE draft_posts SET modified_at = :modifiedAt WHERE draft_id = :draftId")
                .setParameter("modifiedAt", modifiedAt)
                .setParameter("draftId", draftId)
                .executeUpdate();
    }
}
