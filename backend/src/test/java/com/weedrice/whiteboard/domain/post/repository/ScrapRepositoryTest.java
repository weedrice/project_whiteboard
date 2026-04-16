package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.Scrap;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class ScrapRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ScrapRepository scrapRepository;

    private User scrapper;

    @BeforeEach
    void setUp() {
        User author = User.builder()
                .loginId("author")
                .email("author@test.com")
                .password("password")
                .displayName("작성자")
                .build();
        scrapper = User.builder()
                .loginId("scrapper")
                .email("scrapper@test.com")
                .password("password")
                .displayName("스크랩유저")
                .build();
        entityManager.persist(author);
        entityManager.persist(scrapper);

        Board board = Board.builder()
                .boardName("free")
                .boardUrl("free")
                .creator(author)
                .build();
        entityManager.persist(board);

        Post post = Post.builder()
                .board(board)
                .user(author)
                .title("스크랩 대상")
                .contents("contents")
                .isNotice(false)
                .isNsfw(false)
                .isSpoiler(false)
                .isSecret(false)
                .build();
        entityManager.persist(post);

        entityManager.persist(Scrap.builder()
                .user(scrapper)
                .post(post)
                .remark("remark")
                .build());
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("스크랩 목록 전용 조회는 post, post.board, post.user를 함께 로드한다")
    void findPageByUserWithPostDetails_fetchesPostDetails() {
        Page<Scrap> result = scrapRepository.findPageByUserWithPostDetails(scrapper, PageRequest.of(0, 10));
        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();

        assertThat(result.getContent()).hasSize(1);
        Scrap scrap = result.getContent().getFirst();
        assertThat(persistenceUnitUtil.isLoaded(scrap, "post")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(scrap.getPost(), "board")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(scrap.getPost(), "user")).isTrue();
        assertThat(scrap.getPost().getBoard().getBoardName()).isEqualTo("free");
        assertThat(scrap.getPost().getUser().getDisplayName()).isEqualTo("작성자");
    }
}
