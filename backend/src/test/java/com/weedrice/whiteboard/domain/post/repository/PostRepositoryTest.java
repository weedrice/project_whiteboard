package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
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
                board.getBoardId(), category.getCategoryId(), null, null, pageRequest);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Test Post");
    }

    @Test
    @DisplayName("키워드로 게시글 검색 성공")
    void searchPostsByKeyword_success() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<Post> result = postRepository.searchPostsByKeyword("Test", null, pageRequest);

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
                "Test", "TITLE", "test-board", null, pageRequest);

        // then
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Test Post");
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
        Page<Post> result = postRepository.searchPostsByKeyword("Inactive", null, pageRequest);

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
                "Inactive", "TITLE", null, null, pageRequest);

        // then
        assertThat(result.getContent()).isEmpty();
    }
}
