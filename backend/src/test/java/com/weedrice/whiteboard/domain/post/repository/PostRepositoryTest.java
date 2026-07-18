package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.tag.entity.PostTag;
import com.weedrice.whiteboard.domain.tag.entity.Tag;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class PostRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PostRepository postRepository;

    private User user;
    private Board board;
    private BoardCategory category;
    private Post post;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("testuser")
                .email("test@test.com")
                .password("password")
                .displayName("Test User")
                .build();
        entityManager.persist(user);

        board = Board.builder()
                .boardName("Test Board")
                .boardUrl("test-board")
                .creator(user)
                .build();
        entityManager.persist(board);

        category = BoardCategory.builder()
                .name("General")
                .board(board)
                .build();
        entityManager.persist(category);

        post = Post.builder()
                .title("Test Post")
                .contents("Test Contents")
                .user(user)
                .board(board)
                .category(category)
                .build();
        entityManager.persist(post);
        entityManager.flush();
    }

    @Test
    @DisplayName("게시글 ID로 조회 성공")
    void findById_success() {
        // when
        Optional<Post> found = postRepository.findById(post.getPostId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Test Post");
    }

    @Test
    @DisplayName("인기글 후보는 DB에서 기간별 top-N과 결정적 순위를 계산하고 블라인드 글을 제외한다")
    void findPopularRankingCandidates_returnsDatabaseTopNAndExcludesBlindedPosts() {
        LocalDateTime dailySince = LocalDateTime.of(2026, 7, 17, 12, 0);
        LocalDateTime weeklySince = dailySince.minusDays(6);
        Post dailyLowerId = post;
        Post dailyHigherId = persistPost("Daily higher id");
        Post weeklyOnly = persistPost("Weekly only");
        Post blinded = persistPost("Blinded high score");
        Post outsideWindow = persistPost("Outside window");

        updateCreatedAt(dailyLowerId, dailySince.plusHours(1));
        updateCreatedAt(dailyHigherId, dailySince.plusHours(1));
        updateCreatedAt(weeklyOnly, dailySince.minusDays(2));
        updateCreatedAt(blinded, dailySince.plusHours(2));
        updateCreatedAt(outsideWindow, weeklySince);
        updatePopularityScore(dailyLowerId, 10, 1, 0);
        updatePopularityScore(dailyHigherId, 10, 1, 0);
        updatePopularityScore(weeklyOnly, 50, 0, 0);
        updatePopularityScore(blinded, 10_000, 0, 0);
        updatePopularityScore(outsideWindow, 20_000, 0, 0);
        blinded.blind("hidden", dailySince.plusHours(2));
        entityManager.flush();
        entityManager.clear();

        List<PostRepository.PopularRankingProjection> candidates =
                postRepository.findPopularRankingCandidates(dailySince, weeklySince, 2);

        assertThat(candidates)
                .extracting(
                        PostRepository.PopularRankingProjection::getRankingType,
                        PostRepository.PopularRankingProjection::getPostId,
                        PostRepository.PopularRankingProjection::getRankingPosition)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("DAILY", dailyHigherId.getPostId(), 1L),
                        org.assertj.core.groups.Tuple.tuple("DAILY", dailyLowerId.getPostId(), 2L),
                        org.assertj.core.groups.Tuple.tuple("WEEKLY", weeklyOnly.getPostId(), 1L),
                        org.assertj.core.groups.Tuple.tuple("WEEKLY", dailyHigherId.getPostId(), 2L));
    }

    @Test
    @DisplayName("findReportTargetMetadataByPostIds returns author metadata")
    void findReportTargetMetadataByPostIds_returnsAuthorMetadata() {
        List<PostRepository.ReportTargetMetadataProjection> metadata =
                postRepository.findReportTargetMetadataByPostIds(List.of(post.getPostId()));

        assertThat(metadata).singleElement().satisfies(item -> {
            assertThat(item.getTargetId()).isEqualTo(post.getPostId());
            assertThat(item.getTargetUserId()).isEqualTo(user.getUserId());
            assertThat(item.getTargetDisplayName()).isEqualTo("Test User");
            assertThat(item.getTargetLoginId()).isEqualTo("testuser");
        });
    }

    @Test
    @DisplayName("사용자별 게시글 목록 조회 성공")
    void findByUser_success() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<Post> posts = postRepository.findByUserAndIsDeleted(user, false, pageRequest);

        // then
        assertThat(posts.getContent()).isNotEmpty();
        assertThat(posts.getContent().get(0).getUser()).isEqualTo(user);
    }

    @Test
    @DisplayName("관리자 사용자 게시글 목록은 같은 생성 시각에서 postId 내림차순으로 정렬한다")
    void findByUserOrderByCreatedAtDescPostIdDesc_ordersSameCreatedAtByPostIdDesc() {
        Post firstPost = persistPost("First same time post");
        Post secondPost = persistPost("Second same time post");
        entityManager.flush();

        LocalDateTime sameCreatedAt = LocalDateTime.of(2026, 5, 12, 10, 0);
        updateCreatedAt(firstPost, sameCreatedAt);
        updateCreatedAt(secondPost, sameCreatedAt);
        entityManager.flush();
        entityManager.clear();

        Page<Post> result = postRepository.findByUserOrderByCreatedAtDescPostIdDesc(user, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Post::getPostId)
                .containsSubsequence(secondPost.getPostId(), firstPost.getPostId());
    }

    @Test
    @DisplayName("사용자별 게시글 개수 조회 성공")
    void countByUser_success() {
        // when
        long count = postRepository.countByUserAndIsDeleted(user, false);

        // then
        assertThat(count).isGreaterThan(0);
    }

    @Test
    @DisplayName("에이전트 일일 게시글 수는 삭제 게시글을 제외한다")
    void countByAgentAndCreatedAtBetweenAndIsDeletedFalse_excludesDeletedPosts() {
        Agent agent = persistAgent("post-count-agent");
        Post activePost = Post.builder()
                .title("Agent Active Post")
                .contents("Contents")
                .user(user)
                .agent(agent)
                .board(board)
                .category(category)
                .build();
        entityManager.persist(activePost);
        Post deletedPost = Post.builder()
                .title("Agent Deleted Post")
                .contents("Contents")
                .user(user)
                .agent(agent)
                .board(board)
                .category(category)
                .build();
        deletedPost.deletePost();
        entityManager.persist(deletedPost);
        entityManager.flush();

        long count = postRepository.countByAgent_AgentIdAndCreatedAtBetweenAndIsDeletedFalse(
                agent.getAgentId(),
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1));

        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("태그 게시글 조회 기본 정렬은 생성일과 게시글 ID 내림차순이다")
    void findByTagId_defaultSortUsesCreatedAtAndPostIdDesc() {
        Tag tag = Tag.builder().tagName("stable").build();
        entityManager.persist(tag);
        Post olderPost = persistPost("Older tagged post");
        Post firstPost = persistPost("First same time post");
        Post secondPost = persistPost("Second same time post");
        entityManager.persist(PostTag.builder().post(olderPost).tag(tag).build());
        entityManager.persist(PostTag.builder().post(firstPost).tag(tag).build());
        entityManager.persist(PostTag.builder().post(secondPost).tag(tag).build());
        entityManager.flush();

        LocalDateTime sameCreatedAt = LocalDateTime.of(2026, 5, 6, 10, 0);
        updateCreatedAt(firstPost, sameCreatedAt);
        updateCreatedAt(secondPost, sameCreatedAt);
        updateCreatedAt(olderPost, sameCreatedAt.minusMinutes(1));
        entityManager.flush();
        entityManager.clear();

        Page<Post> result = postRepository.findByTagId(tag.getTagId(), Collections.emptyList(), PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(Post::getPostId)
                .containsExactly(secondPost.getPostId(), firstPost.getPostId(), olderPost.getPostId());
    }

    @Test
    @DisplayName("공개 프로필 게시글 수는 공개 활성 노드의 비밀글이 아닌 미삭제 게시글만 집계한다")
    void countPublicProfilePostsByUser_countsOnlyPublicVisiblePosts() {
        Board privateBoard = Board.builder()
                .boardName("Private Board")
                .boardUrl("private-board")
                .creator(user)
                .isPublic(false)
                .build();
        entityManager.persist(privateBoard);

        Board inactiveBoard = Board.builder()
                .boardName("Inactive Board")
                .boardUrl("inactive-board")
                .creator(user)
                .build();
        inactiveBoard.deactivate();
        entityManager.persist(inactiveBoard);

        Post secretPost = Post.builder()
                .title("Secret Post")
                .contents("Secret Contents")
                .user(user)
                .board(board)
                .isSecret(true)
                .build();
        entityManager.persist(secretPost);

        Post deletedPost = Post.builder()
                .title("Deleted Post")
                .contents("Deleted Contents")
                .user(user)
                .board(board)
                .build();
        deletedPost.deletePost();
        entityManager.persist(deletedPost);

        entityManager.persist(Post.builder()
                .title("Private Board Post")
                .contents("Private Contents")
                .user(user)
                .board(privateBoard)
                .build());
        entityManager.persist(Post.builder()
                .title("Inactive Board Post")
                .contents("Inactive Contents")
                .user(user)
                .board(inactiveBoard)
                .build());
        entityManager.flush();

        long count = postRepository.countPublicProfilePostsByUser(user);

        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("홈 랜딩 게시글 수는 공개 활성 노드의 비밀글이 아닌 미삭제 게시글만 집계한다")
    void countPublicLandingVisiblePosts_countsOnlyPublicLandingVisiblePosts() {
        Board privateBoard = persistBoard("Landing Private Board", "landing-private-board", false);
        Board inactiveBoard = persistBoard("Landing Inactive Board", "landing-inactive-board", true);
        inactiveBoard.deactivate();
        Board inquiryBoard = persistBoard("Landing Inquiry Board", "inquiry", true);

        entityManager.persist(landingPost("Landing Visible Post", board, false, false));
        entityManager.persist(landingPost("Landing Secret Post", board, true, false));
        entityManager.persist(landingPost("Landing Deleted Post", board, false, true));
        entityManager.persist(landingPost("Landing Private Board Post", privateBoard, false, false));
        entityManager.persist(landingPost("Landing Inactive Board Post", inactiveBoard, false, false));
        entityManager.persist(landingPost("Landing Inquiry Post", inquiryBoard, false, false));
        entityManager.flush();

        long count = postRepository.countPublicLandingVisiblePosts(BoardPolicyConstants.INQUIRY_BOARD_URL);

        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("노드별 공개 게시글 수는 비밀글과 비공개/비활성 노드을 제외한다")
    void countPublicVisiblePostsByBoardIds_countsOnlyPublicVisiblePosts() {
        Board publicBoard = persistBoard("Count Public Board", "count-public-board", true);
        Board privateBoard = persistBoard("Count Private Board", "count-private-board", false);
        Board inactiveBoard = persistBoard("Count Inactive Board", "count-inactive-board", true);
        inactiveBoard.deactivate();
        Post visiblePost = landingPost("Count Visible Post", publicBoard, false, false);
        Post secretPost = landingPost("Count Secret Post", publicBoard, true, false);
        Post deletedPost = landingPost("Count Deleted Post", publicBoard, false, true);
        Post privateBoardPost = landingPost("Count Private Board Post", privateBoard, false, false);
        Post inactiveBoardPost = landingPost("Count Inactive Board Post", inactiveBoard, false, false);
        entityManager.persist(visiblePost);
        entityManager.persist(secretPost);
        entityManager.persist(deletedPost);
        entityManager.persist(privateBoardPost);
        entityManager.persist(inactiveBoardPost);
        entityManager.flush();
        entityManager.clear();

        List<PostRepository.BoardPostCountProjection> counts = postRepository.countPublicVisiblePostsByBoardIds(
                List.of(publicBoard.getBoardId(), privateBoard.getBoardId(), inactiveBoard.getBoardId()));

        assertThat(counts)
                .filteredOn(count -> count.getBoardId().equals(publicBoard.getBoardId()))
                .singleElement()
                .extracting(PostRepository.BoardPostCountProjection::getPostCount)
                .isEqualTo(1L);
        assertThat(counts)
                .extracting(PostRepository.BoardPostCountProjection::getBoardId)
                .doesNotContain(privateBoard.getBoardId(), inactiveBoard.getBoardId());
    }

    @Test
    @DisplayName("홈 랜딩 날짜별 게시글 수는 공개 랜딩 노출 범위와 기간 조건을 함께 적용한다")
    void countPublicLandingVisiblePostsCreatedBetween_countsOnlyVisiblePostsInRange() {
        LocalDateTime todayStart = LocalDateTime.of(2026, 5, 4, 0, 0);
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        Board privateBoard = persistBoard("Landing Date Private Board", "landing-date-private-board", false);
        Board inactiveBoard = persistBoard("Landing Date Inactive Board", "landing-date-inactive-board", true);
        inactiveBoard.deactivate();
        Board inquiryBoard = persistBoard("Landing Date Inquiry Board", "inquiry", true);

        Post visibleToday = landingPost("Landing Date Visible", board, false, false);
        Post visibleYesterday = landingPost("Landing Date Yesterday", board, false, false);
        Post visibleAtEnd = landingPost("Landing Date End", board, false, false);
        Post secretToday = landingPost("Landing Date Secret", board, true, false);
        Post privateToday = landingPost("Landing Date Private", privateBoard, false, false);
        Post inactiveToday = landingPost("Landing Date Inactive", inactiveBoard, false, false);
        Post inquiryToday = landingPost("Landing Date Inquiry", inquiryBoard, false, false);
        entityManager.persist(visibleToday);
        entityManager.persist(visibleYesterday);
        entityManager.persist(visibleAtEnd);
        entityManager.persist(secretToday);
        entityManager.persist(privateToday);
        entityManager.persist(inactiveToday);
        entityManager.persist(inquiryToday);
        entityManager.flush();

        updateCreatedAt(post, todayStart.minusDays(1));
        updateCreatedAt(visibleToday, todayStart.plusHours(1));
        updateCreatedAt(visibleYesterday, todayStart.minusHours(1));
        updateCreatedAt(visibleAtEnd, tomorrowStart);
        updateCreatedAt(secretToday, todayStart.plusHours(2));
        updateCreatedAt(privateToday, todayStart.plusHours(3));
        updateCreatedAt(inactiveToday, todayStart.plusHours(4));
        updateCreatedAt(inquiryToday, todayStart.plusHours(5));
        entityManager.flush();
        entityManager.clear();

        long count = postRepository.countPublicLandingVisiblePostsCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                todayStart,
                tomorrowStart,
                BoardPolicyConstants.INQUIRY_BOARD_URL);

        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("공개 랜딩 게시글 통계를 한 번에 집계한다")
    void countPublicLandingPostStats_aggregatesVisiblePostCounts() {
        LocalDateTime todayStart = LocalDateTime.of(2026, 5, 4, 0, 0);
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        LocalDateTime yesterdayStart = todayStart.minusDays(1);
        Board privateBoard = persistBoard("Landing Stats Private Board", "landing-stats-private-board", false);
        Board inactiveBoard = persistBoard("Landing Stats Inactive Board", "landing-stats-inactive-board", true);
        inactiveBoard.deactivate();
        Board inquiryBoard = persistBoard("Landing Stats Inquiry Board", "inquiry", true);

        Post visibleToday = landingPost("Landing Stats Today", board, false, false);
        Post visibleYesterday = landingPost("Landing Stats Yesterday", board, false, false);
        Post visibleOld = landingPost("Landing Stats Old", board, false, false);
        Post visibleAtEnd = landingPost("Landing Stats End", board, false, false);
        Post secretToday = landingPost("Landing Stats Secret", board, true, false);
        Post deletedToday = landingPost("Landing Stats Deleted", board, false, true);
        Post privateToday = landingPost("Landing Stats Private", privateBoard, false, false);
        Post inactiveToday = landingPost("Landing Stats Inactive", inactiveBoard, false, false);
        Post inquiryToday = landingPost("Landing Stats Inquiry", inquiryBoard, false, false);
        entityManager.persist(visibleToday);
        entityManager.persist(visibleYesterday);
        entityManager.persist(visibleOld);
        entityManager.persist(visibleAtEnd);
        entityManager.persist(secretToday);
        entityManager.persist(deletedToday);
        entityManager.persist(privateToday);
        entityManager.persist(inactiveToday);
        entityManager.persist(inquiryToday);
        entityManager.flush();

        updateCreatedAt(post, todayStart.minusDays(10));
        updateCreatedAt(visibleToday, todayStart.plusHours(1));
        updateCreatedAt(visibleYesterday, yesterdayStart.plusHours(1));
        updateCreatedAt(visibleOld, todayStart.minusDays(2));
        updateCreatedAt(visibleAtEnd, tomorrowStart);
        updateCreatedAt(secretToday, todayStart.plusHours(2));
        updateCreatedAt(deletedToday, todayStart.plusHours(3));
        updateCreatedAt(privateToday, todayStart.plusHours(4));
        updateCreatedAt(inactiveToday, todayStart.plusHours(5));
        updateCreatedAt(inquiryToday, todayStart.plusHours(6));
        entityManager.flush();
        entityManager.clear();

        PostRepository.PublicLandingPostStatsProjection stats = postRepository.countPublicLandingPostStats(
                todayStart,
                tomorrowStart,
                yesterdayStart,
                BoardPolicyConstants.INQUIRY_BOARD_URL);

        assertThat(stats.getTotalPosts()).isEqualTo(5L);
        assertThat(stats.getPostsToday()).isEqualTo(1L);
        assertThat(stats.getPostsYesterday()).isEqualTo(1L);
    }

    @Test
    @DisplayName("노드별 공지사항 조회 성공")
    void findByBoardAndIsNotice_fetchesSummaryRelations() {
        Agent agent = Agent.builder()
                .user(user)
                .agentTokenHash("notice-agent-token")
                .name("Notice Agent")
                .description("Notice agent")
                .status(Agent.STATUS_ACTIVE)
                .build();
        entityManager.persist(agent);
        Post notice = Post.builder()
                .title("Notice Post")
                .contents("Notice Contents")
                .user(user)
                .agent(agent)
                .board(board)
                .category(category)
                .isNotice(true)
                .build();
        entityManager.persist(notice);
        entityManager.flush();
        entityManager.clear();

        // when
        List<Post> notices = postRepository.findByBoard_BoardIdAndIsNoticeAndIsDeletedOrderByCreatedAtDesc(
                board.getBoardId(), true, false);

        // then
        assertThat(notices).extracting(Post::getPostId).contains(notice.getPostId());
        Post loadedNotice = notices.stream()
                .filter(result -> result.getPostId().equals(notice.getPostId()))
                .findFirst()
                .orElseThrow();
        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();
        assertThat(persistenceUnitUtil.isLoaded(loadedNotice, "user")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(loadedNotice, "agent")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(loadedNotice, "board")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(loadedNotice, "category")).isTrue();
    }

    @Test
    @DisplayName("관리자 문의 목록은 삭제된 게시글을 제외한다")
    void findByBoardAndIsDeletedFalse_excludesDeletedPosts() {
        Board inquiryBoard = persistBoard("Admin Inquiry Board", "inquiry", false);
        Agent agent = persistAgent("Admin Inquiry Agent");
        BoardCategory inquiryCategory = BoardCategory.builder()
                .name("Inquiry")
                .board(inquiryBoard)
                .build();
        entityManager.persist(inquiryCategory);
        Post activePost = Post.builder()
                .title("Active Inquiry")
                .contents("Active Contents")
                .user(user)
                .agent(agent)
                .board(inquiryBoard)
                .category(inquiryCategory)
                .build();
        Post deletedPost = Post.builder()
                .title("Deleted Inquiry")
                .contents("Deleted Contents")
                .user(user)
                .board(inquiryBoard)
                .category(inquiryCategory)
                .build();
        deletedPost.deletePost();
        entityManager.persist(activePost);
        entityManager.persist(deletedPost);
        entityManager.flush();
        entityManager.clear();

        Page<Post> posts = postRepository.findByBoard_BoardIdAndIsDeletedFalse(
                inquiryBoard.getBoardId(),
                PageRequest.of(0, 10, Sort.by("createdAt").descending()));

        assertThat(posts.getContent()).extracting(Post::getTitle)
                .contains("Active Inquiry")
                .doesNotContain("Deleted Inquiry");
        assertThat(posts.getTotalElements()).isEqualTo(1);
        assertThat(posts.getTotalPages()).isEqualTo(1);
        Post loadedPost = posts.getContent().get(0);
        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();
        assertThat(persistenceUnitUtil.isLoaded(loadedPost, "user")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(loadedPost, "agent")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(loadedPost, "board")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(loadedPost, "category")).isTrue();
    }

    @Test
    @DisplayName("요약용 게시글 ID 조회는 노드 작성자까지 함께 로드한다")
    void findByPostIdInAndIsDeletedFalse_fetchesSummaryRelationsAndBoardCreator() {
        User boardCreator = User.builder()
                .loginId("summaryCreator")
                .email("summary-creator@test.com")
                .password("password")
                .displayName("Summary Creator")
                .build();
        entityManager.persist(boardCreator);
        Board summaryBoard = Board.builder()
                .boardName("Summary Board")
                .boardUrl("summary-board")
                .creator(boardCreator)
                .build();
        entityManager.persist(summaryBoard);
        BoardCategory summaryCategory = BoardCategory.builder()
                .name("Summary")
                .board(summaryBoard)
                .build();
        entityManager.persist(summaryCategory);
        Agent agent = Agent.builder()
                .user(user)
                .agentTokenHash("summary-agent-token")
                .name("Summary Agent")
                .description("Summary agent")
                .status(Agent.STATUS_ACTIVE)
                .build();
        entityManager.persist(agent);
        Post summaryPost = Post.builder()
                .title("Summary Post")
                .contents("Summary Contents")
                .user(user)
                .agent(agent)
                .board(summaryBoard)
                .category(summaryCategory)
                .build();
        entityManager.persist(summaryPost);
        entityManager.flush();
        entityManager.clear();

        List<Post> posts = postRepository.findByPostIdInAndIsDeletedFalse(List.of(summaryPost.getPostId()));

        assertThat(posts).hasSize(1);
        Post loadedPost = posts.get(0);
        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();
        assertThat(persistenceUnitUtil.isLoaded(loadedPost, "user")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(loadedPost, "agent")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(loadedPost, "board")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(loadedPost.getBoard(), "creator")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(loadedPost, "category")).isTrue();
    }

    @Test
    @DisplayName("최근 본 글 hydrate 조회는 블라인드 게시글을 제외한다")
    void findByPostIdInAndIsDeletedFalseAndIsBlindedFalse_excludesBlindedPosts() {
        Post visiblePost = persistPost("Visible history post");
        Post blindedPost = persistPost("Blinded history post");
        blindedPost.blind("reported", LocalDateTime.now());
        entityManager.flush();
        entityManager.clear();

        List<Post> posts = postRepository.findByPostIdInAndIsDeletedFalseAndIsBlindedFalse(
                List.of(visiblePost.getPostId(), blindedPost.getPostId()));

        assertThat(posts).extracting(Post::getPostId).containsExactly(visiblePost.getPostId());
    }

    @Test
    @DisplayName("조회수 원자 증가")
    void incrementViewCount_success() {
        Long postId = post.getPostId();
        entityManager.flush();
        entityManager.clear();
        LocalDateTime modifiedAt = postRepository.findById(postId).orElseThrow().getModifiedAt();

        int updated = postRepository.incrementViewCount(postId);
        entityManager.flush();
        entityManager.clear();

        Post updatedPost = postRepository.findById(postId).orElseThrow();

        assertThat(updated).isEqualTo(1);
        assertThat(updatedPost.getViewCount()).isEqualTo(1);
        assertThat(updatedPost.getModifiedAt()).isEqualTo(modifiedAt);
    }

    @Test
    @DisplayName("조회수 증가는 삭제되지 않은 게시글만 갱신한다")
    void incrementViewCount_updatesOnlyActivePost() {
        Long activePostId = post.getPostId();
        Post deletedPost = persistPost("Deleted View Count Post");
        deletedPost.deletePost();
        Long deletedPostId = deletedPost.getPostId();
        entityManager.flush();
        entityManager.clear();

        assertThat(postRepository.incrementViewCount(activePostId)).isEqualTo(1);
        assertThat(postRepository.incrementViewCount(deletedPostId)).isZero();
        entityManager.flush();
        entityManager.clear();

        assertThat(postRepository.findById(activePostId).orElseThrow().getViewCount()).isEqualTo(1);
        assertThat(postRepository.findById(deletedPostId).orElseThrow().getViewCount()).isZero();
    }

    @Test
    @DisplayName("Agent posts respect pageable sort")
    void findByAgent_respectsPageableSort() {
        Agent agent = Agent.builder()
                .user(user)
                .agentTokenHash("agent-token")
                .name("agent")
                .description("desc")
                .status(Agent.STATUS_ACTIVE)
                .build();
        entityManager.persist(agent);

        Post leastLikedPost = Post.builder()
                .title("Least Liked Post")
                .contents("Contents")
                .user(user)
                .agent(agent)
                .board(board)
                .category(category)
                .build();
        entityManager.persist(leastLikedPost);

        Post mostLikedPost = Post.builder()
                .title("Most Liked Post")
                .contents("Contents")
                .user(user)
                .agent(agent)
                .board(board)
                .category(category)
                .build();
        mostLikedPost.incrementLikeCount();
        entityManager.persist(mostLikedPost);
        entityManager.flush();
        entityManager.clear();

        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "likeCount"));

        Page<Post> result = postRepository.findByAgent_AgentIdAndIsDeleted(
                agent.getAgentId(),
                false,
                pageRequest);

        assertThat(result.getContent()).extracting(Post::getTitle)
                .containsExactly("Most Liked Post", "Least Liked Post");
    }

    @Test
    @DisplayName("노드 삭제 시 게시글 삭제 성공")
    void deleteByBoard_success() {
        // when
        postRepository.deleteByBoard(board);
        entityManager.flush();
        entityManager.clear();

        // then
        Optional<Post> found = postRepository.findById(post.getPostId());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("노드 ID와 카테고리 ID로 게시글 조회 성공")
    void findByBoardIdAndCategoryId_success() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        // when
        Page<Post> result = postRepository.findByBoardIdAndCategoryId(
                board.getBoardId(), category.getCategoryId(), null, null, null, false, null, pageRequest);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Test Post");
    }

    @Test
    @DisplayName("custom 게시글 조회는 에이전트 관계를 함께 로딩한다")
    void customPostQueries_fetchAgentRelation() {
        Agent agent = Agent.builder()
                .user(user)
                .agentTokenHash("agent-token")
                .name("agent")
                .description("desc")
                .status(Agent.STATUS_ACTIVE)
                .build();
        entityManager.persist(agent);

        Post agentPost = Post.builder()
                .title("Agent Post")
                .contents("Contents")
                .user(user)
                .agent(agent)
                .board(board)
                .category(category)
                .build();
        entityManager.persist(agentPost);
        entityManager.flush();
        entityManager.clear();

        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();

        Page<Post> boardPosts = postRepository.findByBoardIdAndCategoryId(
                board.getBoardId(), category.getCategoryId(), "Agent Post", null, null, false, null,
                PageRequest.of(0, 10));
        Post boardPost = boardPosts.getContent().stream()
                .filter(result -> result.getPostId().equals(agentPost.getPostId()))
                .findFirst()
                .orElseThrow();

        assertThat(persistenceUnitUtil.isLoaded(boardPost, "agent")).isTrue();

        entityManager.clear();

        Post detailPost = postRepository.findByIdWithRelations(agentPost.getPostId()).orElseThrow();

        assertThat(persistenceUnitUtil.isLoaded(detailPost, "agent")).isTrue();
    }

    @Test
    @DisplayName("게시글 목록 projection은 요약 필드와 제한된 본문 preview를 조회한다")
    void findPostListSummariesByBoardIdAndCategoryId_success() {
        Agent agent = Agent.builder()
                .user(user)
                .agentTokenHash("agent-token")
                .name("agent")
                .description("desc")
                .status(Agent.STATUS_ACTIVE)
                .build();
        entityManager.persist(agent);

        user.updateProfileImage("profile.png");
        board.update("Test Board", null, "board-icon.png", 0, false, true, true);

        Post projectionPost = Post.builder()
                .title("Long Projection Post")
                .contents("<p>" + "A".repeat(PostRepositoryCustomImpl.POST_LIST_CONTENT_PREVIEW_LENGTH + 500) + "</p>")
                .user(user)
                .agent(agent)
                .board(board)
                .category(category)
                .isNotice(true)
                .isNsfw(true)
                .isSpoiler(true)
                .isSecret(true)
                .build();
        projectionPost.incrementViewCount();
        projectionPost.incrementViewCount();
        projectionPost.incrementLikeCount();
        projectionPost.incrementCommentCount();
        entityManager.persist(projectionPost);
        entityManager.flush();
        entityManager.clear();

        Page<PostListSummaryProjection> result = postRepository.findPostListSummariesByBoardIdAndCategoryId(
                board.getBoardId(),
                category.getCategoryId(),
                "Long Projection",
                null,
                null,
                true,
                user.getUserId(),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(result.getContent()).singleElement().satisfies(summary -> {
            assertThat(summary.postId()).isEqualTo(projectionPost.getPostId());
            assertThat(summary.title()).isEqualTo("Long Projection Post");
            assertThat(summary.boardId()).isEqualTo(board.getBoardId());
            assertThat(summary.categoryId()).isEqualTo(category.getCategoryId());
            assertThat(summary.userId()).isEqualTo(user.getUserId());
            assertThat(summary.boardUrl()).isEqualTo("test-board");
            assertThat(summary.boardName()).isEqualTo("Test Board");
            assertThat(summary.boardIconUrl()).isEqualTo("board-icon.png");
            assertThat(summary.categoryName()).isEqualTo("General");
            assertThat(summary.userDisplayName()).isEqualTo("Test User");
            assertThat(summary.userProfileImageUrl()).isEqualTo("profile.png");
            assertThat(summary.agentId()).isNotNull();
            assertThat(summary.agentName()).isEqualTo("agent");
            assertThat(summary.viewCount()).isEqualTo(2);
            assertThat(summary.likeCount()).isEqualTo(1);
            assertThat(summary.commentCount()).isEqualTo(1);
            assertThat(summary.isNotice()).isTrue();
            assertThat(summary.isNsfw()).isTrue();
            assertThat(summary.isSpoiler()).isTrue();
            assertThat(summary.isSecret()).isTrue();
            assertThat(summary.createdAt()).isNotNull();
            assertThat(summary.contentPreview())
                    .hasSize(PostRepositoryCustomImpl.POST_LIST_CONTENT_PREVIEW_LENGTH)
                    .startsWith("<p>AAA");
        });
    }

    @Test
    @DisplayName("Count posts before target in default board order")
    void countPostsBeforeInBoardDefaultOrder_success() {
        // given
        Post newestPost = Post.builder()
                .title("Newest Post")
                .contents("Contents")
                .user(user)
                .board(board)
                .category(category)
                .build();
        Post secondNewestPost = Post.builder()
                .title("Second Newest Post")
                .contents("Contents")
                .user(user)
                .board(board)
                .category(category)
                .build();
        Post olderPost = Post.builder()
                .title("Older Post")
                .contents("Contents")
                .user(user)
                .board(board)
                .category(category)
                .build();
        entityManager.persist(newestPost);
        entityManager.persist(secondNewestPost);
        entityManager.persist(olderPost);
        entityManager.flush();

        LocalDateTime now = LocalDateTime.of(2026, 4, 23, 12, 0);
        updateCreatedAt(newestPost, now.plusMinutes(2));
        updateCreatedAt(secondNewestPost, now.plusMinutes(1));
        updateCreatedAt(post, now);
        updateCreatedAt(olderPost, now.minusMinutes(1));
        entityManager.flush();
        entityManager.clear();

        Post target = postRepository.findById(post.getPostId()).orElseThrow();

        // when
        long count = postRepository.countPostsBeforeInBoardDefaultOrder(
                board.getBoardId(), target.getCreatedAt(), target.getPostId(), null, false, null);

        // then
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("Search posts by keyword")
    void searchPostsByKeyword_success() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<Post> result = postRepository.searchPostsByKeyword("Test", null, null, pageRequest);

        // then
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().get(0).getTitle()).contains("Test");
    }

    @Test
    @DisplayName("복합 조건으로 게시글 검색 성공")
    void searchPosts_success() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<Post> result = postRepository.searchPosts(
                "Test", "TITLE", "test-board", null, false, null, pageRequest);

        // then
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Test Post");
    }

    @Test
    @DisplayName("작성자 검색은 일반 사용자 표시명을 찾는다")
    void searchPosts_authorType_matchesUserDisplayName() {
        PageRequest pageRequest = PageRequest.of(0, 10);

        Page<Post> result = postRepository.searchPosts(
                "Test User", "AUTHOR", "test-board", null, false, null, pageRequest);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent())
                .extracting(Post::getTitle)
                .contains("Test Post");
    }

    @Test
    @DisplayName("작성자 검색은 에이전트 표시명을 찾는다")
    void searchPosts_authorType_matchesAgentName() {
        Agent agent = Agent.builder()
                .user(user)
                .agentTokenHash("agent-token")
                .name("Novi Helper")
                .description("desc")
                .status(Agent.STATUS_ACTIVE)
                .build();
        entityManager.persist(agent);

        Post agentPost = Post.builder()
                .title("Agent Authored Post")
                .contents("Contents")
                .user(user)
                .agent(agent)
                .board(board)
                .category(category)
                .build();
        entityManager.persist(agentPost);
        entityManager.flush();
        entityManager.clear();

        PageRequest pageRequest = PageRequest.of(0, 10);

        Page<Post> result = postRepository.searchPosts(
                "Novi Helper", "AUTHOR", "test-board", null, false, null, pageRequest);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent())
                .extracting(Post::getTitle)
                .containsExactly("Agent Authored Post");
    }

    @Test
    @DisplayName("인기 게시글 조회 성공 (이미지 포함)")
    void findTrendingPosts_success() {
        // given
        File file = File.builder()
                .originalName("test.jpg")
                .mimeType("image/jpeg")
                .fileSize(1024L)
                .filePath("/uploads/test.jpg")
                .uploader(user)
                .relatedType("POST_CONTENT")
                .relatedId(post.getPostId())
                .build();
        entityManager.persist(file);

        post.incrementViewCount();
        post.incrementLikeCount();
        entityManager.persist(post);
        entityManager.flush();

        PageRequest pageRequest = PageRequest.of(0, 10);
        LocalDateTime since = LocalDateTime.now().minusDays(7);

        // when
        List<Post> trendingPosts = postRepository.findTrendingPosts(since, Collections.emptyList(), pageRequest);

        // then
        assertThat(trendingPosts).isNotEmpty();
        assertThat(trendingPosts.get(0).getPostId()).isEqualTo(post.getPostId());
    }

    @Test
    @DisplayName("인기 게시글 조회는 점수 동점일 때 생성일과 게시글 ID로 안정 정렬한다")
    void findPublicLandingLatestPosts_ordersVisiblePostsByCreatedAtAndPostId() {
        User blockedUser = User.builder()
                .loginId("landing-blocked")
                .email("landing-blocked@test.com")
                .password("password")
                .displayName("Landing Blocked")
                .build();
        entityManager.persist(blockedUser);

        Board privateBoard = persistBoard("Landing Latest Private", "landing-latest-private", false);
        Board inactiveBoard = persistBoard("Landing Latest Inactive", "landing-latest-inactive", true);
        inactiveBoard.deactivate();
        Board inquiryBoard = persistBoard("Landing Latest Inquiry", "inquiry", true);

        Post visibleOld = landingPost("Visible Old", board, false, false);
        Post visibleSameTimeOlderId = landingPost("Visible Same Time Older Id", board, false, false);
        Post visibleSameTimeNewerId = landingPost("Visible Same Time Newer Id", board, false, false);
        Post secretPost = landingPost("Latest Secret", board, true, false);
        Post deletedPost = landingPost("Latest Deleted", board, false, true);
        Post privatePost = landingPost("Latest Private Board", privateBoard, false, false);
        Post inactivePost = landingPost("Latest Inactive Board", inactiveBoard, false, false);
        Post inquiryPost = landingPost("Latest Inquiry Board", inquiryBoard, false, false);
        Post blockedPost = trendingPost("Latest Blocked", "Contents", blockedUser, board, false);
        Post noticePost = Post.builder()
                .title("Latest Notice")
                .contents("Contents")
                .user(user)
                .board(board)
                .category(category)
                .isNotice(true)
                .build();
        entityManager.persist(visibleOld);
        entityManager.persist(visibleSameTimeOlderId);
        entityManager.persist(visibleSameTimeNewerId);
        entityManager.persist(secretPost);
        entityManager.persist(deletedPost);
        entityManager.persist(privatePost);
        entityManager.persist(inactivePost);
        entityManager.persist(inquiryPost);
        entityManager.persist(blockedPost);
        entityManager.persist(noticePost);
        entityManager.flush();

        LocalDateTime baseCreatedAt = LocalDateTime.now().minusHours(2);
        updateCreatedAt(post, baseCreatedAt.minusDays(1));
        updateCreatedAt(visibleOld, baseCreatedAt);
        updateCreatedAt(visibleSameTimeOlderId, baseCreatedAt.plusMinutes(1));
        updateCreatedAt(visibleSameTimeNewerId, baseCreatedAt.plusMinutes(1));
        updateCreatedAt(secretPost, baseCreatedAt.plusMinutes(10));
        updateCreatedAt(deletedPost, baseCreatedAt.plusMinutes(10));
        updateCreatedAt(privatePost, baseCreatedAt.plusMinutes(10));
        updateCreatedAt(inactivePost, baseCreatedAt.plusMinutes(10));
        updateCreatedAt(inquiryPost, baseCreatedAt.plusMinutes(10));
        updateCreatedAt(blockedPost, baseCreatedAt.plusMinutes(10));
        updateCreatedAt(noticePost, baseCreatedAt.plusMinutes(10));
        entityManager.flush();
        entityManager.clear();

        List<Post> latestPosts = postRepository.findPublicLandingLatestPosts(
                BoardPolicyConstants.INQUIRY_BOARD_URL,
                List.of(blockedUser.getUserId()),
                PageRequest.of(0, 3));

        assertThat(latestPosts)
                .extracting(Post::getTitle)
                .containsExactly(
                        "Visible Same Time Newer Id",
                        "Visible Same Time Older Id",
                        "Visible Old");
    }

    @Test
    @DisplayName("홈 랜딩 최신글은 공개 노드의 공개 글을 최신순으로 조회한다")
    void findTrendingPosts_ordersTiesByCreatedAtAndPostId() {
        Post sameCreatedOlderId = post;
        Post sameCreatedNewerId = Post.builder()
                .title("Same Created Newer")
                .contents("Test Contents")
                .user(user)
                .board(board)
                .category(category)
                .build();
        Post latestCreated = Post.builder()
                .title("Latest Created")
                .contents("Test Contents")
                .user(user)
                .board(board)
                .category(category)
                .build();
        entityManager.persist(sameCreatedNewerId);
        entityManager.persist(latestCreated);
        entityManager.flush();

        persistPostContentImage(sameCreatedOlderId);
        persistPostContentImage(sameCreatedNewerId);
        persistPostContentImage(latestCreated);

        LocalDateTime baseCreatedAt = LocalDateTime.now().minusHours(2);
        updateCreatedAt(sameCreatedOlderId, baseCreatedAt);
        updateCreatedAt(sameCreatedNewerId, baseCreatedAt);
        updateCreatedAt(latestCreated, baseCreatedAt.plusMinutes(1));
        entityManager.flush();
        entityManager.clear();

        List<Post> trendingPosts = postRepository.findTrendingPosts(
                baseCreatedAt.minusHours(1),
                Collections.emptyList(),
                PageRequest.of(0, 10));

        assertThat(trendingPosts)
                .extracting(Post::getPostId)
                .containsSubsequence(
                        latestCreated.getPostId(),
                        sameCreatedNewerId.getPostId(),
                        sameCreatedOlderId.getPostId());
    }

    @Test
    @DisplayName("countTrendingPosts는 인기 게시글 목록과 동일한 가시성 및 미디어 조건을 사용한다")
    void countTrendingPosts_matchesTrendingListConditions() {
        User blockedUser = User.builder()
                .loginId("blocked")
                .email("blocked@test.com")
                .password("password")
                .displayName("Blocked User")
                .build();
        entityManager.persist(blockedUser);

        Board privateBoard = persistBoard("Private Board", "private-board", false);
        Board inactiveBoard = persistBoard("Inactive Board", "inactive-board", true);
        inactiveBoard.deactivate();

        LocalDateTime baseCreatedAt = LocalDateTime.now().minusHours(2);
        Post attachedImagePost = trendingPost("Attached Image", "plain content", user, board, false);
        Post imgTagPost = trendingPost("Image Tag", "<img src=\"/image.jpg\">", user, board, false);
        Post iframePost = trendingPost("Iframe", "<iframe src=\"/video\"></iframe>", user, board, false);
        Post oldPost = trendingPost("Old", "<img src=\"/old.jpg\">", user, board, false);
        Post secretPost = trendingPost("Secret", "<img src=\"/secret.jpg\">", user, board, true);
        Post noticePost = Post.builder()
                .title("Notice")
                .contents("<img src=\"/notice.jpg\">")
                .user(user)
                .board(board)
                .category(category)
                .isNotice(true)
                .build();
        Post privateBoardPost = trendingPost("Private", "<img src=\"/private.jpg\">", user, privateBoard, false);
        Post inactiveBoardPost = trendingPost("Inactive", "<img src=\"/inactive.jpg\">", user, inactiveBoard, false);
        Post blockedPost = trendingPost("Blocked", "<img src=\"/blocked.jpg\">", blockedUser, board, false);
        entityManager.persist(attachedImagePost);
        entityManager.persist(imgTagPost);
        entityManager.persist(iframePost);
        entityManager.persist(oldPost);
        entityManager.persist(secretPost);
        entityManager.persist(noticePost);
        entityManager.persist(privateBoardPost);
        entityManager.persist(inactiveBoardPost);
        entityManager.persist(blockedPost);
        entityManager.flush();

        persistPostContentImage(attachedImagePost);
        updateCreatedAt(attachedImagePost, baseCreatedAt);
        updateCreatedAt(imgTagPost, baseCreatedAt.plusMinutes(1));
        updateCreatedAt(iframePost, baseCreatedAt.plusMinutes(2));
        updateCreatedAt(oldPost, baseCreatedAt.minusDays(10));
        updateCreatedAt(secretPost, baseCreatedAt.plusMinutes(3));
        updateCreatedAt(noticePost, baseCreatedAt.plusMinutes(4));
        updateCreatedAt(privateBoardPost, baseCreatedAt.plusMinutes(4));
        updateCreatedAt(inactiveBoardPost, baseCreatedAt.plusMinutes(5));
        updateCreatedAt(blockedPost, baseCreatedAt.plusMinutes(6));
        entityManager.flush();
        entityManager.clear();

        LocalDateTime since = baseCreatedAt.minusHours(1);
        List<Long> blockedUserIds = List.of(blockedUser.getUserId());
        List<Post> trendingPosts = postRepository.findTrendingPosts(since, blockedUserIds, PageRequest.of(0, 20));

        assertThat(postRepository.countTrendingPosts(since, blockedUserIds)).isEqualTo(trendingPosts.size());
        assertThat(trendingPosts)
                .extracting(Post::getTitle)
                .containsExactly("Iframe", "Image Tag", "Attached Image");
    }

    @Test
    @DisplayName("트렌딩 첨부 이미지 조건은 활성 파일만 미디어로 집계한다")
    void findTrendingPosts_excludesInactiveAttachedImages() {
        LocalDateTime baseCreatedAt = LocalDateTime.now().minusHours(2);
        Post activeImagePost = trendingPost("Active Image", "plain content", user, board, false);
        Post pendingDeleteImagePost = trendingPost("Pending Delete Image", "plain content", user, board, false);
        Post deleteFailedImagePost = trendingPost("Delete Failed Image", "plain content", user, board, false);
        Post htmlImagePost = trendingPost("Html Image", "<img src=\"/still-visible.jpg\">", user, board, false);
        entityManager.persist(activeImagePost);
        entityManager.persist(pendingDeleteImagePost);
        entityManager.persist(deleteFailedImagePost);
        entityManager.persist(htmlImagePost);
        entityManager.flush();

        persistPostContentImage(activeImagePost, FileStorageStatus.ACTIVE);
        persistPostContentImage(pendingDeleteImagePost, FileStorageStatus.PENDING_DELETE);
        persistPostContentImage(deleteFailedImagePost, FileStorageStatus.DELETE_FAILED);
        persistPostContentImage(htmlImagePost, FileStorageStatus.PENDING_DELETE);
        updateCreatedAt(activeImagePost, baseCreatedAt);
        updateCreatedAt(pendingDeleteImagePost, baseCreatedAt.plusMinutes(1));
        updateCreatedAt(deleteFailedImagePost, baseCreatedAt.plusMinutes(2));
        updateCreatedAt(htmlImagePost, baseCreatedAt.plusMinutes(3));
        entityManager.flush();
        entityManager.clear();

        LocalDateTime since = baseCreatedAt.minusHours(1);
        List<Post> trendingPosts = postRepository.findTrendingPosts(
                since,
                Collections.emptyList(),
                PageRequest.of(0, 20));

        assertThat(postRepository.countTrendingPosts(since, Collections.emptyList())).isEqualTo(trendingPosts.size());
        assertThat(trendingPosts)
                .extracting(Post::getTitle)
                .contains("Active Image", "Html Image")
                .doesNotContain("Pending Delete Image", "Delete Failed Image");
    }

    @Test
    @DisplayName("비활성 노드의 게시글은 키워드 검색에서 제외됨")
    void searchPostsByKeyword_inactiveBoard_excluded() {
        // given
        Board inactiveBoard = Board.builder()
                .boardName("Inactive Board")
                .boardUrl("inactive-board")
                .creator(user)
                .build();
        inactiveBoard.deactivate();
        entityManager.persist(inactiveBoard);

        BoardCategory inactiveCategory = BoardCategory.builder()
                .name("General")
                .board(inactiveBoard)
                .build();
        entityManager.persist(inactiveCategory);

        Post inactivePost = Post.builder()
                .title("Inactive Post")
                .contents("Inactive Contents")
                .user(user)
                .board(inactiveBoard)
                .category(inactiveCategory)
                .build();
        entityManager.persist(inactivePost);
        entityManager.flush();

        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<Post> result = postRepository.searchPostsByKeyword("Inactive", null, null, pageRequest);

        // then
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("비활성 노드의 게시글은 복합 조건 검색에서 제외됨")
    void searchPosts_inactiveBoard_excluded() {
        // given
        Board inactiveBoard = Board.builder()
                .boardName("Inactive Board 2")
                .boardUrl("inactive-board-2")
                .creator(user)
                .build();
        inactiveBoard.deactivate();
        entityManager.persist(inactiveBoard);

        BoardCategory inactiveCategory = BoardCategory.builder()
                .name("General")
                .board(inactiveBoard)
                .build();
        entityManager.persist(inactiveCategory);

        Post inactivePost = Post.builder()
                .title("Inactive Post 2")
                .contents("Inactive Contents 2")
                .user(user)
                .board(inactiveBoard)
                .category(inactiveCategory)
                .build();
        entityManager.persist(inactivePost);
        entityManager.flush();

        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<Post> result = postRepository.searchPosts(
                "Inactive", "TITLE", null, null, false, null, pageRequest);

        // then
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("관리자 대시보드 게시글 집계는 삭제되지 않은 게시글만 포함한다")
    void countVisiblePostsForAdminDashboard_excludesDeletedPosts() {
        Post visiblePost = Post.builder()
                .title("Visible Post")
                .contents("Visible Contents")
                .user(user)
                .board(board)
                .category(category)
                .build();
        entityManager.persist(visiblePost);

        Post deletedPost = Post.builder()
                .title("Deleted Post")
                .contents("Deleted Contents")
                .user(user)
                .board(board)
                .category(category)
                .build();
        deletedPost.deletePost();
        entityManager.persist(deletedPost);
        entityManager.flush();

        assertThat(postRepository.countVisiblePostsForAdminDashboard()).isEqualTo(2L);
    }

    @Test
    @DisplayName("댓글 수 증가는 삭제되지 않은 게시글만 갱신한다")
    void incrementCommentCount_updatesOnlyActivePost() {
        Long activePostId = post.getPostId();
        Post deletedPost = persistPost("Deleted Comment Count Post");
        deletedPost.deletePost();
        Long deletedPostId = deletedPost.getPostId();
        entityManager.flush();
        entityManager.clear();

        assertThat(postRepository.incrementCommentCount(activePostId)).isEqualTo(1);
        assertThat(postRepository.incrementCommentCount(deletedPostId)).isZero();
        entityManager.flush();
        entityManager.clear();

        assertThat(postRepository.findById(activePostId).orElseThrow().getCommentCount()).isEqualTo(1);
        assertThat(postRepository.findById(deletedPostId).orElseThrow().getCommentCount()).isZero();
    }

    @Test
    @DisplayName("댓글 수 감소는 삭제되지 않은 게시글만 갱신한다")
    void decrementCommentCount_updatesOnlyActivePost() {
        post.incrementCommentCount();
        Long activePostId = post.getPostId();
        Post deletedPost = persistPost("Deleted Comment Count Post");
        deletedPost.incrementCommentCount();
        deletedPost.deletePost();
        Long deletedPostId = deletedPost.getPostId();
        entityManager.flush();
        entityManager.clear();

        assertThat(postRepository.decrementCommentCount(activePostId)).isEqualTo(1);
        assertThat(postRepository.decrementCommentCount(deletedPostId)).isZero();
        entityManager.flush();
        entityManager.clear();

        assertThat(postRepository.findById(activePostId).orElseThrow().getCommentCount()).isZero();
        assertThat(postRepository.findById(deletedPostId).orElseThrow().getCommentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("좋아요 수 증가는 삭제되지 않은 게시글만 갱신한다")
    void incrementLikeCount_updatesOnlyActivePost() {
        Long activePostId = post.getPostId();
        Post deletedPost = persistPost("Deleted Like Count Post");
        deletedPost.deletePost();
        Long deletedPostId = deletedPost.getPostId();
        entityManager.flush();
        entityManager.clear();

        assertThat(postRepository.incrementLikeCount(activePostId)).isEqualTo(1);
        assertThat(postRepository.incrementLikeCount(deletedPostId)).isZero();
        entityManager.flush();
        entityManager.clear();

        assertThat(postRepository.findById(activePostId).orElseThrow().getLikeCount()).isEqualTo(1);
        assertThat(postRepository.findById(deletedPostId).orElseThrow().getLikeCount()).isZero();
    }

    @Test
    @DisplayName("좋아요 수 감소는 삭제되지 않은 게시글만 갱신한다")
    void decrementLikeCount_updatesOnlyActivePost() {
        post.incrementLikeCount();
        Long activePostId = post.getPostId();
        Post deletedPost = persistPost("Deleted Like Count Post");
        deletedPost.incrementLikeCount();
        deletedPost.deletePost();
        Long deletedPostId = deletedPost.getPostId();
        entityManager.flush();
        entityManager.clear();

        assertThat(postRepository.decrementLikeCount(activePostId)).isEqualTo(1);
        assertThat(postRepository.decrementLikeCount(deletedPostId)).isZero();
        entityManager.flush();
        entityManager.clear();

        assertThat(postRepository.findById(activePostId).orElseThrow().getLikeCount()).isZero();
        assertThat(postRepository.findById(deletedPostId).orElseThrow().getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("좋아요 수 조회는 삭제 게시글을 제외한다")
    void findLikeCountByPostId_excludesDeletedPost() {
        Long activePostId = post.getPostId();
        Post deletedPost = persistPost("Deleted Like Count Post");
        deletedPost.incrementLikeCount();
        deletedPost.deletePost();
        Long deletedPostId = deletedPost.getPostId();
        entityManager.flush();
        entityManager.clear();

        assertThat(postRepository.findLikeCountByPostId(activePostId)).isZero();
        assertThat(postRepository.findLikeCountByPostId(deletedPostId)).isNull();
    }

    @Test
    @DisplayName("조회수 조회는 삭제 게시글을 제외한다")
    void findViewCountByPostId_excludesDeletedPost() {
        Long activePostId = post.getPostId();
        post.incrementViewCount();
        Post deletedPost = persistPost("Deleted View Count Post");
        deletedPost.incrementViewCount();
        deletedPost.deletePost();
        Long deletedPostId = deletedPost.getPostId();
        entityManager.flush();
        entityManager.clear();

        assertThat(postRepository.findViewCountByPostId(activePostId)).isEqualTo(1);
        assertThat(postRepository.findViewCountByPostId(deletedPostId)).isNull();
    }

    @Test
    @DisplayName("findByIdWithRelationsForUpdate는 비관적 쓰기 잠금을 선언한다")
    void findByIdWithRelationsForUpdate_declaresPessimisticWriteLock() throws NoSuchMethodException {
        var method = PostRepository.class.getMethod("findByIdWithRelationsForUpdate", Long.class);

        Lock lock = method.getAnnotation(Lock.class);
        EntityGraph entityGraph = method.getAnnotation(EntityGraph.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(entityGraph).isNotNull();
        assertThat(Arrays.asList(entityGraph.attributePaths()))
                .containsExactlyInAnyOrder("user", "agent", "board", "category");
    }

    @Test
    @DisplayName("findActiveByIdAndBoardIdForUpdate는 게시글, 노드, 삭제 상태를 함께 검증한다")
    void findActiveByIdAndBoardIdForUpdate_filtersByBoardAndDeletedState() {
        Board otherBoard = Board.builder()
                .boardName("Other Board")
                .boardUrl("other-board")
                .creator(user)
                .build();
        entityManager.persist(otherBoard);

        Post deletedPost = Post.builder()
                .title("Deleted Post")
                .contents("Deleted Contents")
                .user(user)
                .board(board)
                .category(category)
                .build();
        deletedPost.deletePost();
        entityManager.persist(deletedPost);
        entityManager.flush();
        entityManager.clear();

        Optional<Post> active = postRepository.findActiveByIdAndBoardIdForUpdate(
                post.getPostId(),
                board.getBoardId());
        Optional<Post> boardMismatch = postRepository.findActiveByIdAndBoardIdForUpdate(
                post.getPostId(),
                otherBoard.getBoardId());
        Optional<Post> deleted = postRepository.findActiveByIdAndBoardIdForUpdate(
                deletedPost.getPostId(),
                board.getBoardId());

        assertThat(active).isPresent();
        assertThat(boardMismatch).isEmpty();
        assertThat(deleted).isEmpty();
    }

    @Test
    @DisplayName("findActiveByIdAndBoardIdForUpdate는 비관적 쓰기 잠금을 선언한다")
    void findActiveByIdAndBoardIdForUpdate_declaresPessimisticWriteLock() throws NoSuchMethodException {
        var method = PostRepository.class.getMethod("findActiveByIdAndBoardIdForUpdate", Long.class, Long.class);

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    private void updateCreatedAt(Post targetPost, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE posts SET created_at = :createdAt WHERE post_id = :postId")
                .setParameter("createdAt", createdAt)
                .setParameter("postId", targetPost.getPostId())
                .executeUpdate();
    }

    private void updatePopularityScore(Post targetPost, int viewCount, int likeCount, int commentCount) {
        entityManager.getEntityManager()
                .createNativeQuery("""
                        UPDATE posts
                        SET view_count = :viewCount,
                            like_count = :likeCount,
                            comment_count = :commentCount
                        WHERE post_id = :postId
                        """)
                .setParameter("viewCount", viewCount)
                .setParameter("likeCount", likeCount)
                .setParameter("commentCount", commentCount)
                .setParameter("postId", targetPost.getPostId())
                .executeUpdate();
    }

    private Post persistPost(String title) {
        Post targetPost = Post.builder()
                .title(title)
                .contents("contents")
                .user(user)
                .board(board)
                .category(category)
                .build();
        entityManager.persist(targetPost);
        return targetPost;
    }

    private Agent persistAgent(String name) {
        Agent targetAgent = Agent.builder()
                .user(user)
                .agentTokenHash(name + "-token")
                .name(name)
                .description("desc")
                .status(Agent.STATUS_ACTIVE)
                .build();
        entityManager.persist(targetAgent);
        return targetAgent;
    }

    private Board persistBoard(String boardName, String boardUrl, boolean isPublic) {
        Board targetBoard = Board.builder()
                .boardName(boardName)
                .boardUrl(boardUrl)
                .creator(user)
                .isPublic(isPublic)
                .build();
        entityManager.persist(targetBoard);
        return targetBoard;
    }

    private Post landingPost(String title, Board targetBoard, boolean isSecret, boolean isDeleted) {
        Post targetPost = Post.builder()
                .title(title)
                .contents("Contents")
                .user(user)
                .board(targetBoard)
                .category(category)
                .isSecret(isSecret)
                .build();
        if (isDeleted) {
            targetPost.deletePost();
        }
        return targetPost;
    }

    private Post trendingPost(String title, String contents, User author, Board targetBoard, boolean isSecret) {
        return Post.builder()
                .title(title)
                .contents(contents)
                .user(author)
                .board(targetBoard)
                .category(category)
                .isSecret(isSecret)
                .build();
    }

    private void persistPostContentImage(Post targetPost) {
        persistPostContentImage(targetPost, FileStorageStatus.ACTIVE);
    }

    private void persistPostContentImage(Post targetPost, FileStorageStatus storageStatus) {
        File file = File.builder()
                .originalName("test-" + targetPost.getPostId() + ".jpg")
                .mimeType("image/jpeg")
                .fileSize(1024L)
                .filePath("/uploads/test-" + targetPost.getPostId() + ".jpg")
                .uploader(user)
                .relatedType("POST_CONTENT")
                .relatedId(targetPost.getPostId())
                .storageStatus(storageStatus)
                .build();
        entityManager.persist(file);
    }
}
