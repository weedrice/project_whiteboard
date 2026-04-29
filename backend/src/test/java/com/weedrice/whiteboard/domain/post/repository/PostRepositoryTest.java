package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.post.entity.Post;
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
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
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
    @DisplayName("사용자별 게시글 개수 조회 성공")
    void countByUser_success() {
        // when
        long count = postRepository.countByUserAndIsDeleted(user, false);

        // then
        assertThat(count).isGreaterThan(0);
    }

    @Test
    @DisplayName("게시판별 공지사항 조회 성공")
    void findByBoardAndIsNotice_success() {
        // when
        List<Post> notices = postRepository.findByBoard_BoardIdAndIsNoticeAndIsDeletedOrderByCreatedAtDesc(
                board.getBoardId(), true, false);

        // then
        assertThat(notices).isNotNull();
    }

    @Test
    @DisplayName("특정 날짜 이후 게시글 조회 성공")
    void findByCreatedAtAfter_success() {
        // given
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);

        // when
        List<Post> posts = postRepository.findByCreatedAtAfterAndIsDeleted(yesterday, false);

        // then
        assertThat(posts).isNotEmpty();
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
    @DisplayName("게시판 삭제 시 게시글 삭제 성공")
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
    @DisplayName("게시판 ID와 카테고리 ID로 게시글 조회 성공")
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
    @DisplayName("비활성 게시판의 게시글은 키워드 검색에서 제외됨")
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
    @DisplayName("비활성 게시판의 게시글은 복합 조건 검색에서 제외됨")
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

    private void updateCreatedAt(Post targetPost, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE posts SET created_at = :createdAt WHERE post_id = :postId")
                .setParameter("createdAt", createdAt)
                .setParameter("postId", targetPost.getPostId())
                .executeUpdate();
    }
}
