package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.Scrap;
import com.weedrice.whiteboard.domain.post.entity.ScrapFolder;
import com.weedrice.whiteboard.domain.post.entity.ScrapId;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
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

    private User author;
    private User scrapper;
    private Board board;
    private Post scrappedPost;

    @BeforeEach
    void setUp() {
        author = User.builder()
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

        board = Board.builder()
                .boardName("free")
                .boardUrl("free")
                .creator(author)
                .build();
        entityManager.persist(board);

        scrappedPost = Post.builder()
                .board(board)
                .user(author)
                .title("스크랩 대상")
                .contents("contents")
                .isNotice(false)
                .isNsfw(false)
                .isSpoiler(false)
                .isSecret(false)
                .build();
        entityManager.persist(scrappedPost);

        entityManager.persist(Scrap.builder()
                .user(scrapper)
                .post(scrappedPost)
                .remark("remark")
                .build());
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("스크랩 목록 전용 조회는 post, post.board, post.user를 함께 로드한다")
    void findPageByUserWithPostDetails_fetchesPostDetails() {
        Page<Scrap> result = scrapRepository.findPageByUserWithPostDetails(
                scrapper,
                false,
                true,
                NO_BLOCKED_USER_IDS,
                BoardPolicyConstants.INQUIRY_BOARD_URL,
                true,
                PageRequest.of(0, 10));
        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();

        assertThat(result.getContent()).hasSize(1);
        Scrap scrap = result.getContent().getFirst();
        assertThat(persistenceUnitUtil.isLoaded(scrap, "post")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(scrap.getPost(), "board")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(scrap.getPost(), "user")).isTrue();
        assertThat(scrap.getPost().getBoard().getBoardName()).isEqualTo("free");
        assertThat(scrap.getPost().getUserId()).isEqualTo(author.getUserId());
    }

    @Test
    @DisplayName("스크랩 검색 조회는 제목과 메모를 검색한다")
    void findPageByUserWithPostDetailsByKeyword_filtersTitleAndRemark() {
        User managedAuthor = entityManager.find(User.class, author.getUserId());
        User managedScrapper = entityManager.find(User.class, scrapper.getUserId());
        Board managedBoard = entityManager.find(Board.class, board.getBoardId());
        Post remarkMatchedPost = persistPost(managedBoard, managedAuthor, "다른 제목", false);
        entityManager.persist(Scrap.builder()
                .user(managedScrapper)
                .post(remarkMatchedPost)
                .remark("target memo")
                .build());
        entityManager.flush();
        entityManager.clear();

        Page<Scrap> result = scrapRepository.findPageByUserWithPostDetailsByKeyword(
                scrapper,
                null,
                "%target%",
                false,
                true,
                NO_BLOCKED_USER_IDS,
                BoardPolicyConstants.INQUIRY_BOARD_URL,
                true,
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(scrap -> scrap.getPost().getTitle())
                .containsExactly("다른 제목");
    }

    @Test
    @DisplayName("스크랩 검색은 퍼센트와 밑줄을 와일드카드가 아닌 문자로 처리한다")
    void findPageByUserWithPostDetailsByKeyword_treatsWildcardsAsLiterals() {
        User managedAuthor = entityManager.find(User.class, author.getUserId());
        User managedScrapper = entityManager.find(User.class, scrapper.getUserId());
        Board managedBoard = entityManager.find(Board.class, board.getBoardId());
        Post literalPost = persistPost(managedBoard, managedAuthor, "달성률 100%_완료!", false);
        Post wildcardOnlyPost = persistPost(managedBoard, managedAuthor, "달성률 100점A완료", false);
        entityManager.persist(Scrap.builder().user(managedScrapper).post(literalPost).remark(null).build());
        entityManager.persist(Scrap.builder().user(managedScrapper).post(wildcardOnlyPost).remark(null).build());
        entityManager.flush();
        entityManager.clear();

        Page<Scrap> result = scrapRepository.findPageByUserWithPostDetailsByKeyword(
                scrapper,
                null,
                "%100!%!_완료!!%",
                false,
                true,
                NO_BLOCKED_USER_IDS,
                BoardPolicyConstants.INQUIRY_BOARD_URL,
                true,
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent())
                .extracting(scrap -> scrap.getPost().getTitle())
                .containsExactly("달성률 100%_완료!");
    }

    @Test
    @DisplayName("findPostIdsByUserIdAndPostIdIn returns only scrapped post IDs")
    void findPostIdsByUserIdAndPostIdIn_success() {
        List<Long> postIds = scrapRepository.findPostIdsByUserIdAndPostIdIn(
                scrapper.getUserId(),
                List.of(scrappedPost.getPostId(), -1L));

        assertThat(postIds).containsExactly(scrappedPost.getPostId());
    }

    @Test
    @DisplayName("같은 스크랩 시각의 목록은 postId 내림차순으로 조회된다")
    void findPageByUserWithPostDetails_ordersByCreatedAtAndPostIdDesc() {
        User managedAuthor = entityManager.find(User.class, author.getUserId());
        User managedScrapper = entityManager.find(User.class, scrapper.getUserId());
        Board managedBoard = entityManager.find(Board.class, board.getBoardId());
        Post newerPost = persistPost(managedBoard, managedAuthor, "newer-post-id", false);
        entityManager.persist(Scrap.builder()
                .user(managedScrapper)
                .post(newerPost)
                .remark("newer")
                .build());
        entityManager.flush();

        LocalDateTime sameCreatedAt = LocalDateTime.of(2030, 1, 1, 0, 0);
        updateScrapCreatedAt(managedScrapper.getUserId(), scrappedPost.getPostId(), sameCreatedAt);
        updateScrapCreatedAt(managedScrapper.getUserId(), newerPost.getPostId(), sameCreatedAt);
        entityManager.clear();

        Page<Scrap> result = scrapRepository.findPageByUserWithPostDetails(
                scrapper,
                false,
                true,
                NO_BLOCKED_USER_IDS,
                BoardPolicyConstants.INQUIRY_BOARD_URL,
                true,
                PageRequest.of(0, 10, Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("post.postId"))));

        assertThat(result.getContent())
                .extracting(scrap -> scrap.getPost().getPostId())
                .containsExactly(newerPost.getPostId(), scrappedPost.getPostId());
    }

    @Test
    @DisplayName("findPageByUserWithPostDetails filters invisible posts")
    void findPageByUserWithPostDetails_filtersInvisiblePosts() {
        User blockedAuthor = persistUser("blocked-author", "blocked-author@test.com", "차단작성자");
        Board blockedBoard = persistBoard("blocked-board", blockedAuthor, true);
        Post blockedPost = persistPost(blockedBoard, blockedAuthor, "차단된 작성자 글", false);

        User otherAuthor = persistUser("other-author", "other-author@test.com", "다른작성자");
        Board publicBoard = persistBoard("public-board", otherAuthor, true);
        Post deletedPost = persistPost(publicBoard, otherAuthor, "삭제 글", false);
        deletedPost.deletePost();

        Board privateBoard = persistBoard("private-board", otherAuthor, false);
        Post privatePost = persistPost(privateBoard, otherAuthor, "비공개 노드 글", false);

        Board inactiveBoard = persistBoard("inactive-board", otherAuthor, true);
        inactiveBoard.deactivate();
        Post inactivePost = persistPost(inactiveBoard, otherAuthor, "비활성 노드 글", false);

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
                BoardPolicyConstants.INQUIRY_BOARD_URL,
                true,
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(scrap -> scrap.getPost().getTitle())
                .containsExactly("스크랩 대상");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("스크랩 목록은 본인 제한 게시글을 노출한다")
    void findPageByUserWithPostDetails_allowsOwnRestrictedInquiryPosts() {
        User managedScrapper = entityManager.find(User.class, scrapper.getUserId());
        Board ownBoard = persistBoard("inquiry", managedScrapper, false);
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
                scrapper,
                false,
                true,
                NO_BLOCKED_USER_IDS,
                BoardPolicyConstants.INQUIRY_BOARD_URL,
                true,
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(scrap -> scrap.getPost().getTitle())
                .contains("내 비밀글");
    }

    @Test
    @DisplayName("레거시 전환 후 스크랩 목록은 본인 문의 게시글도 제외한다")
    void findPageByUserWithPostDetails_excludesOwnInquiryAfterLegacyCutover() {
        User managedScrapper = entityManager.find(User.class, scrapper.getUserId());
        Board inquiryBoard = persistBoard("inquiry", managedScrapper, false);
        Post ownInquiryPost = persistPost(inquiryBoard, managedScrapper, "숨겨진 내 문의", true);
        entityManager.persist(Scrap.builder()
                .user(managedScrapper)
                .post(ownInquiryPost)
                .remark("legacy")
                .build());
        entityManager.flush();
        entityManager.clear();

        Page<Scrap> result = scrapRepository.findPageByUserWithPostDetails(
                scrapper,
                false,
                true,
                NO_BLOCKED_USER_IDS,
                BoardPolicyConstants.INQUIRY_BOARD_URL,
                false,
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(scrap -> scrap.getPost().getTitle())
                .containsExactly("스크랩 대상");
        assertThat(result.getTotalElements()).isEqualTo(1L);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "reader", "author", "boardAdmin", "superAdmin" })
    @DisplayName("블라인드 글은 권한과 관계없이 전체·폴더·검색 스크랩 목록과 개수에서 제외한다")
    void findScrapPages_excludeBlindedPostsFromContentAndCount(String viewerRole) {
        User managedAuthor = entityManager.find(User.class, author.getUserId());
        User managedScrapper = entityManager.find(User.class, scrapper.getUserId());
        Board managedBoard = entityManager.find(Board.class, board.getBoardId());
        if ("boardAdmin".equals(viewerRole)) {
            entityManager.persist(Admin.builder()
                    .user(managedScrapper).board(managedBoard).role("MANAGER").build());
        }
        ScrapFolder folder = entityManager.persist(ScrapFolder.builder()
                .user(managedScrapper).name("saved").build());
        Scrap existingScrap = entityManager.find(Scrap.class,
                new ScrapId(scrapper.getUserId(), scrappedPost.getPostId()));
        existingScrap.moveToFolder(folder);
        Post visiblePost = persistPost(managedBoard, managedAuthor, "visible", false);
        Post blindedPost = persistPost(managedBoard,
                "author".equals(viewerRole) ? managedScrapper : managedAuthor, "blinded", false);
        blindedPost.blind("POLICY", LocalDateTime.of(2030, 1, 1, 0, 0));
        for (Post post : List.of(visiblePost, blindedPost)) {
            entityManager.persist(Scrap.builder()
                    .user(managedScrapper).post(post).remark("remark").folder(folder).build());
        }
        entityManager.flush();
        // Equal timestamps put the newest (blinded) post first unless the query excludes it.
        for (Post post : List.of(scrappedPost, visiblePost, blindedPost)) {
            updateScrapCreatedAt(scrapper.getUserId(), post.getPostId(),
                    LocalDateTime.of(2030, 1, 1, 0, 0));
        }
        Long folderId = folder.getFolderId();
        Long visiblePostId = visiblePost.getPostId();
        entityManager.clear();

        for (boolean filterFolder : List.of(false, true)) {
            for (boolean search : List.of(false, true)) {
                // A full first page forces Spring Data to execute the separate count query.
                PageRequest page = PageRequest.of(0, 1);
                Long requestedFolderId = filterFolder ? folderId : null;
                boolean superAdmin = "superAdmin".equals(viewerRole);
                Page<Scrap> result = search
                        ? scrapRepository.findPageByUserWithPostDetailsByKeyword(
                                scrapper, requestedFolderId, "%remark%", superAdmin, true,
                                NO_BLOCKED_USER_IDS, BoardPolicyConstants.INQUIRY_BOARD_URL, true, page)
                        : scrapRepository.findPageByUserWithPostDetails(
                                scrapper, requestedFolderId, superAdmin, true,
                                NO_BLOCKED_USER_IDS, BoardPolicyConstants.INQUIRY_BOARD_URL, true, page);

                assertThat(result.getContent())
                        .as("visible content: role=%s, folder=%s, search=%s", viewerRole, filterFolder, search)
                        .extracting(scrap -> scrap.getPost().getPostId())
                        .containsExactly(visiblePostId);
                assertThat(result.getTotalElements())
                        .as("visible count: role=%s, folder=%s, search=%s", viewerRole, filterFolder, search)
                        .isEqualTo(2);
                assertThat(result.getTotalPages()).isEqualTo(2);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void scrapPages_filterAuthorEligibilityBeforeLimitForListFolderAndSearch(boolean superAdmin) {
        LocalDateTime now = LocalDateTime.now();
        User managedScrapper = entityManager.find(User.class, scrapper.getUserId());
        Board managedBoard = entityManager.find(Board.class, board.getBoardId());
        ScrapFolder folder = entityManager.persist(ScrapFolder.builder().user(managedScrapper).name("eligibility").build());
        entityManager.find(Scrap.class, new ScrapId(scrapper.getUserId(), scrappedPost.getPostId())).moveToFolder(folder);
        User expired = persistUser("scrap-expired", "expired@scrap.test", "expired");
        User future = persistUser("scrap-future", "future@scrap.test", "future");
        User muted = persistUser("scrap-muted", "muted@scrap.test", "muted");
        User banned = persistUser("scrap-banned", "banned@scrap.test", "banned");
        User permanent = persistUser("scrap-permanent", "permanent@scrap.test", "permanent");
        User inactive = persistUser("scrap-inactive", "inactive@scrap.test", "inactive");
        User deleted = persistUser("scrap-deleted", "deleted@scrap.test", "deleted");
        User blocked = persistUser("scrap-blocked", "blocked@scrap.test", "blocked");
        inactive.suspend();
        deleted.delete(now.minusDays(1));
        persistScrapSanction(managedScrapper, expired, "BAN", now.minusDays(2), now.minusDays(1));
        persistScrapSanction(managedScrapper, future, "BAN", now.plusDays(1), null);
        persistScrapSanction(managedScrapper, muted, "MUTE", now.minusDays(1), null);
        persistScrapSanction(managedScrapper, banned, "BAN", now.minusDays(1), now.plusDays(1));
        persistScrapSanction(managedScrapper, banned, "ban", now.minusDays(2), null);
        persistScrapSanction(managedScrapper, permanent, "BAN", now.minusDays(1), null);
        List<Post> addedPosts = new java.util.ArrayList<>();
        for (User target : List.of(expired, future, muted, banned, permanent, inactive, deleted, blocked)) {
            Post post = persistPost(managedBoard, target, "remark", false);
            addedPosts.add(post);
            entityManager.persist(Scrap.builder().user(managedScrapper).post(post).remark("remark").folder(folder).build());
        }
        entityManager.flush();
        updateScrapCreatedAt(scrapper.getUserId(), scrappedPost.getPostId(), now);
        for (Post post : addedPosts) {
            updateScrapCreatedAt(scrapper.getUserId(), post.getPostId(), now);
        }
        entityManager.clear();

        for (boolean filterFolder : List.of(false, true)) {
            for (boolean search : List.of(false, true)) {
                Long requestedFolder = filterFolder ? folder.getFolderId() : null;
                Page<Scrap> first = visibleScrapPage(search, requestedFolder, superAdmin, blocked.getUserId(), 0);
                Page<Scrap> second = visibleScrapPage(search, requestedFolder, superAdmin, blocked.getUserId(), 1);
                assertThat(first.getContent()).extracting(saved -> saved.getPost().getPostId())
                        .containsExactly(addedPosts.get(2).getPostId(), addedPosts.get(1).getPostId());
                assertThat(second.getContent()).extracting(saved -> saved.getPost().getPostId())
                        .containsExactly(addedPosts.get(0).getPostId(), scrappedPost.getPostId());
                assertThat(first.getTotalElements()).isEqualTo(4);
                assertThat(second.getTotalElements()).isEqualTo(4);
                assertThat(first.getTotalPages()).isEqualTo(2);
            }
        }
        // Visibility filtering does not delete the user's saved records or change owned write lookups.
        assertThat(scrapRepository.findOwnedByPostIdForUpdate(scrapper.getUserId(), addedPosts.get(3).getPostId()))
                .isPresent();
    }

    private Page<Scrap> visibleScrapPage(boolean search, Long folderId, boolean superAdmin, Long blockedId, int page) {
        return search
                ? scrapRepository.findPageByUserWithPostDetailsByKeyword(scrapper, folderId, "%remark%", superAdmin,
                        false, List.of(blockedId), BoardPolicyConstants.INQUIRY_BOARD_URL, true, PageRequest.of(page, 2))
                : scrapRepository.findPageByUserWithPostDetails(scrapper, folderId, superAdmin, false,
                        List.of(blockedId), BoardPolicyConstants.INQUIRY_BOARD_URL, true, PageRequest.of(page, 2));
    }

    private void persistScrapSanction(User processor, User target, String type, LocalDateTime start, LocalDateTime end) {
        entityManager.persist(Sanction.builder().targetUser(target).processorUser(processor)
                .type(type).startDate(start).endDate(end).build());
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

    private void updateScrapCreatedAt(Long userId, Long postId, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("""
                        UPDATE scraps
                        SET created_at = :createdAt
                        WHERE user_id = :userId
                          AND post_id = :postId
                        """)
                .setParameter("createdAt", createdAt)
                .setParameter("userId", userId)
                .setParameter("postId", postId)
                .executeUpdate();
    }
}
