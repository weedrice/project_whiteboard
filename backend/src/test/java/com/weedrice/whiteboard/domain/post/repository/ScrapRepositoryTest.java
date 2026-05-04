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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class ScrapRepositoryTest {

    private static final List<Long> NO_BLOCKED_USER_IDS = List.of(-1L);

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
        Page<Scrap> result = scrapRepository.findPageByUserWithPostDetails(
                scrapper, false, true, NO_BLOCKED_USER_IDS, PageRequest.of(0, 10));
        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();

        assertThat(result.getContent()).hasSize(1);
        Scrap scrap = result.getContent().getFirst();
        assertThat(persistenceUnitUtil.isLoaded(scrap, "post")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(scrap.getPost(), "board")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(scrap.getPost(), "user")).isTrue();
        assertThat(scrap.getPost().getBoard().getBoardName()).isEqualTo("free");
        assertThat(scrap.getPost().getUser().getDisplayName()).isEqualTo("작성자");
    }

    @Test
    @DisplayName("스크랩 목록은 접근할 수 없는 게시글 메타데이터를 제외한다")
    void findPageByUserWithPostDetails_filtersInvisiblePosts() {
        User blockedAuthor = persistUser("blocked-author", "blocked-author@test.com", "차단작성자");
        Board blockedBoard = persistBoard("blocked-board", blockedAuthor, true);
        Post blockedPost = persistPost(blockedBoard, blockedAuthor, "차단된 작성자 글", false);

        User otherAuthor = persistUser("other-author", "other-author@test.com", "다른작성자");
        Board publicBoard = persistBoard("public-board", otherAuthor, true);
        Post deletedPost = persistPost(publicBoard, otherAuthor, "삭제 글", false);
        deletedPost.deletePost();

        Board privateBoard = persistBoard("private-board", otherAuthor, false);
        Post privatePost = persistPost(privateBoard, otherAuthor, "비공개 게시판 글", false);

        Board inactiveBoard = persistBoard("inactive-board", otherAuthor, true);
        inactiveBoard.deactivate();
        Post inactivePost = persistPost(inactiveBoard, otherAuthor, "비활성 게시판 글", false);

        Post secretPost = persistPost(publicBoard, otherAuthor, "비밀글", true);

        User managedScrapper = entityManager.find(User.class, scrapper.getUserId());
        for (Post post : List.of(blockedPost, deletedPost, privatePost, inactivePost, secretPost)) {
            entityManager.persist(Scrap.builder()
                    .user(managedScrapper)
                    .post(post)
                    .remark("hidden")
                    .build());
        }
        entityManager.flush();
        entityManager.clear();

        Page<Scrap> result = scrapRepository.findPageByUserWithPostDetails(
                scrapper,
                false,
                false,
                List.of(blockedAuthor.getUserId()),
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(scrap -> scrap.getPost().getTitle())
                .containsExactly("스크랩 대상");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("스크랩 목록은 본인 제한 게시글을 노출한다")
    void findPageByUserWithPostDetails_allowsOwnRestrictedPosts() {
        User managedScrapper = entityManager.find(User.class, scrapper.getUserId());
        Board ownBoard = persistBoard("own-private-board", managedScrapper, false);
        ownBoard.deactivate();
        Post ownSecretPost = persistPost(ownBoard, managedScrapper, "내 비밀글", true);
        entityManager.persist(Scrap.builder()
                .user(managedScrapper)
                .post(ownSecretPost)
                .remark("own")
                .build());
        entityManager.flush();
        entityManager.clear();

        Page<Scrap> result = scrapRepository.findPageByUserWithPostDetails(
                scrapper, false, true, NO_BLOCKED_USER_IDS, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(scrap -> scrap.getPost().getTitle())
                .contains("내 비밀글");
    }

    private User persistUser(String loginId, String email, String displayName) {
        User user = User.builder()
                .loginId(loginId)
                .email(email)
                .password("password")
                .displayName(displayName)
                .build();
        entityManager.persist(user);
        return user;
    }

    private Board persistBoard(String boardUrl, User creator, boolean isPublic) {
        Board board = Board.builder()
                .boardName(boardUrl)
                .boardUrl(boardUrl)
                .creator(creator)
                .isPublic(isPublic)
                .build();
        entityManager.persist(board);
        return board;
    }

    private Post persistPost(Board board, User author, String title, boolean isSecret) {
        Post post = Post.builder()
                .board(board)
                .user(author)
                .title(title)
                .contents("contents")
                .isNotice(false)
                .isNsfw(false)
                .isSpoiler(false)
                .isSecret(isSecret)
                .build();
        entityManager.persist(post);
        return post;
    }
}
