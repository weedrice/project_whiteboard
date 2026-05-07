package com.weedrice.whiteboard.domain.board.repository;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.board.entity.Board;
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
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class BoardRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BoardRepository boardRepository;

    private User creator;
    private Board board;

    @BeforeEach
    void setUp() {
        creator = User.builder()
                .loginId("creator")
                .email("creator@test.com")
                .password("password")
                .displayName("Creator")
                .build();
        entityManager.persist(creator);

        board = Board.builder()
                .boardName("Test Board")
                .boardUrl("test-board")
                .creator(creator)
                .build();
        entityManager.persist(board);
        entityManager.flush();
    }

    @Test
    @DisplayName("게시판 URL로 조회 성공")
    void findByBoardUrl_success() {
        // when
        Optional<Board> found = boardRepository.findByBoardUrl("test-board");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getBoardName()).isEqualTo("Test Board");
    }

    @Test
    @DisplayName("게시판 저장 및 조회 성공")
    void saveAndFind_success() {
        // given
        Board newBoard = Board.builder()
                .boardName("New Board")
                .boardUrl("new-board")
                .creator(creator)
                .build();

        // when
        Board saved = boardRepository.save(newBoard);
        Optional<Board> found = boardRepository.findById(saved.getBoardId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getBoardName()).isEqualTo("New Board");
    }

    @Test
    @DisplayName("홈 랜딩 게시판 수는 문의 게시판을 제외한 공개 활성 게시판만 집계한다")
    void countPublicLandingVisibleBoards_countsOnlyPublicActiveNonInquiryBoards() {
        persistBoard("Landing Public Board", "landing-public-board", 10, true, true);
        persistBoard("Landing Private Board", "landing-private-board", 20, true, false);
        persistBoard("Landing Inactive Board", "landing-inactive-board", 30, false, true);
        persistBoard("Landing Inquiry Board", "Inquiry", 40, true, true);
        entityManager.flush();

        long count = boardRepository.countPublicLandingVisibleBoards();

        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("보드 이름 검색 미리보기는 공개 활성 게시판만 정렬된 5개로 제한")
    void findBoardPreviewByKeyword_returnsOnlyPublicActiveBoardsWithinLimit() {
        persistBoard("Search 1", "search-1", 30, true, true);
        persistBoard("Search 2", "search-2", 10, true, true);
        persistBoard("Search 3", "search-3", 20, true, true);
        persistBoard("Search 4", "search-4", 40, true, true);
        persistBoard("Search 5", "search-5", 50, true, true);
        persistBoard("Search 6", "search-6", 60, true, true);
        persistBoard("Search Private", "search-private", 5, true, false);
        persistBoard("Search Inactive", "search-inactive", 1, false, true);
        persistBoard("Other Board", "other-board", 2, true, true);
        entityManager.flush();

        var boards = boardRepository
                .findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc(
                        "search",
                        PageRequest.of(0, 5));

        assertThat(boards).hasSize(5);
        assertThat(boards).extracting(Board::getBoardName)
                .containsExactly("Search 2", "Search 3", "Search 1", "Search 4", "Search 5");
    }

    @Test
    @DisplayName("보드 이름 검색 미리보기는 sortOrder 동률일 때 boardId로 안정 정렬")
    void findBoardPreviewByKeyword_ordersByBoardIdWhenSortOrderMatches() {
        Board first = persistBoard("Search Tie A", "search-tie-a", 10, true, true);
        Board second = persistBoard("Search Tie B", "search-tie-b", 10, true, true);
        entityManager.flush();

        var boards = boardRepository
                .findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc(
                        "search tie",
                        PageRequest.of(0, 5));

        assertThat(boards).extracting(Board::getBoardId)
                .containsExactly(first.getBoardId(), second.getBoardId());
    }

    @Test
    @DisplayName("인기 게시판 조회는 공개 활성 게시판만 남기고 동률은 sortOrder와 boardId로 정렬한다")
    void findTopPublicBoardsByPostCount_filtersAndOrdersByTieBreakers() {
        Board visibleTop = persistBoard("Visible Top", "visible-top", 30, true, true);
        Board visibleTieFirst = persistBoard("Visible Tie First", "visible-tie-first", 10, true, true);
        Board visibleTieSecond = persistBoard("Visible Tie Second", "visible-tie-second", 10, true, true);
        Board deletedOnlyBoard = persistBoard("Deleted Only", "deleted-only", 15, true, true);
        Board secretOnlyBoard = persistBoard("Secret Only", "secret-only", 5, true, true);
        Board privateBoard = persistBoard("Private Top", "private-top", 1, true, false);
        Board inactiveBoard = persistBoard("Inactive Top", "inactive-top", 2, false, true);
        Board inquiryBoard = persistBoard("Inquiry Top", "Inquiry", 3, true, true);

        persistPosts(visibleTop, 4);
        persistPosts(visibleTieFirst, 2);
        persistPosts(visibleTieSecond, 2);
        persistDeletedPosts(deletedOnlyBoard, 3);
        persistSecretPosts(secretOnlyBoard, 8);
        persistPosts(privateBoard, 6);
        persistPosts(inactiveBoard, 5);
        persistPosts(inquiryBoard, 7);

        entityManager.flush();
        entityManager.clear();

        var boardIds = boardRepository.findTopPublicBoardIdsByPostCount(PageRequest.of(0, 10));
        var boards = boardRepository.findByBoardIdIn(boardIds);

        assertThat(boardIds).containsExactly(
                visibleTop.getBoardId(),
                visibleTieFirst.getBoardId(),
                visibleTieSecond.getBoardId());
        assertThat(boards).extracting(Board::getBoardName)
                .containsExactlyInAnyOrder("Visible Top", "Visible Tie First", "Visible Tie Second");
        assertThat(boardIds).doesNotContain(secretOnlyBoard.getBoardId());
    }

    @Test
    @DisplayName("Readable active boards filter public creator admin super admin and inquiry")
    void findReadableActiveBoardsOrderBySortOrderAscBoardIdAsc_filtersReadableBoards() {
        User reader = persistUser("reader");
        Board creatorPrivateBoard = persistBoard("Creator Private", "creator-private", 10, true, false, reader);
        Board adminPrivateBoard = persistBoard("Admin Private", "admin-private", 20, true, false);
        Board otherPrivateBoard = persistBoard("Other Private", "other-private", 30, true, false);
        Board inactivePublicBoard = persistBoard("Inactive Public", "inactive-public", 40, false, true);
        Board inquiryBoard = persistBoard("Inquiry Board", "inquiry", 50, true, true);
        entityManager.persist(Admin.builder()
                .user(reader)
                .board(adminPrivateBoard)
                .role("BOARD_ADMIN")
                .build());
        entityManager.flush();
        entityManager.clear();

        var readableBoards = boardRepository.findReadableActiveBoardsOrderBySortOrderAscBoardIdAsc(reader, false);
        var superAdminBoards = boardRepository.findReadableActiveBoardsOrderBySortOrderAscBoardIdAsc(reader, true);

        assertThat(readableBoards).extracting(Board::getBoardName)
                .contains("Test Board", "Creator Private", "Admin Private")
                .doesNotContain("Other Private", "Inactive Public", "Inquiry Board");
        assertThat(superAdminBoards).extracting(Board::getBoardName)
                .contains("Test Board", "Creator Private", "Admin Private", "Other Private")
                .doesNotContain("Inactive Public", "Inquiry Board");
        assertThat(creatorPrivateBoard).isNotNull();
        assertThat(otherPrivateBoard).isNotNull();
        assertThat(inactivePublicBoard).isNotNull();
        assertThat(inquiryBoard).isNotNull();
    }

    @Test
    @DisplayName("Readable active boards for anonymous user include public only")
    void findReadableActiveBoardsOrderBySortOrderAscBoardIdAsc_anonymousIncludesPublicOnly() {
        persistBoard("Private Board", "private-board", 10, true, false);
        persistBoard("Inquiry Board", "inquiry", 20, true, true);
        persistBoard("Inactive Public", "inactive-public", 30, false, true);
        entityManager.flush();
        entityManager.clear();

        var boards = boardRepository.findReadableActiveBoardsOrderBySortOrderAscBoardIdAsc(null, false);

        assertThat(boards).extracting(Board::getBoardName)
                .contains("Test Board")
                .doesNotContain("Private Board", "Inquiry Board", "Inactive Public");
    }

    @Test
    @DisplayName("Board list queries use boardId as stable tie breaker")
    void boardListQueries_orderByBoardIdWhenSortOrderTies() {
        Board sameSortFirst = persistBoard("Same Sort First", "same-sort-first", 0, true, true);
        Board sameSortSecond = persistBoard("Same Sort Second", "same-sort-second", 0, true, true);
        entityManager.flush();
        entityManager.clear();

        List<Long> expectedOrder = List.of(
                board.getBoardId(),
                sameSortFirst.getBoardId(),
                sameSortSecond.getBoardId());

        var publicBoards = boardRepository.findByIsActiveAndIsPublicOrderBySortOrderAscBoardIdAsc(true, true);
        var readableBoards = boardRepository.findReadableActiveBoardsOrderBySortOrderAscBoardIdAsc(null, false);
        var activeBoards = boardRepository.findByIsActiveOrderBySortOrderAscBoardIdAsc(true);
        var allBoards = boardRepository.findAllByOrderBySortOrderAscBoardIdAsc();

        assertThat(publicBoards).extracting(Board::getBoardId).containsExactlyElementsOf(expectedOrder);
        assertThat(readableBoards).extracting(Board::getBoardId).containsExactlyElementsOf(expectedOrder);
        assertThat(activeBoards).extracting(Board::getBoardId).containsExactlyElementsOf(expectedOrder);
        assertThat(allBoards).extracting(Board::getBoardId).containsExactlyElementsOf(expectedOrder);
    }

    @Test
    @DisplayName("Readable top boards filter visibility before limit and keep order")
    void findTopReadableBoardIdsByPostCount_filtersVisibilityBeforeLimit() {
        User reader = persistUser("top-reader");
        Board publicBoard = persistBoard("Readable Public", "readable-public", 20, true, true);
        Board secretOnlyPublicBoard = persistBoard("Readable Secret Only Public", "readable-secret-only-public", 5,
                true, true);
        Board adminPrivateBoard = persistBoard("Readable Admin Private", "readable-admin-private", 10, true, false);
        Board otherPrivateBoard = persistBoard("Unreadable Private", "unreadable-private", 1, true, false);
        Board inquiryBoard = persistBoard("Inquiry Top", "inquiry", 2, true, true);
        Board deletedOnlyBoard = persistBoard("Deleted Only Top", "deleted-only-top", 3, true, true);
        entityManager.persist(Admin.builder()
                .user(reader)
                .board(adminPrivateBoard)
                .role("BOARD_ADMIN")
                .build());

        persistPosts(publicBoard, 2);
        persistSecretPosts(secretOnlyPublicBoard, 20);
        persistPosts(adminPrivateBoard, 5);
        persistPosts(otherPrivateBoard, 9);
        persistPosts(inquiryBoard, 8);
        persistDeletedPosts(deletedOnlyBoard, 7);
        entityManager.flush();
        entityManager.clear();

        var boardIds = boardRepository.findTopReadableBoardIdsByPostCount(reader, false, PageRequest.of(0, 10));

        assertThat(boardIds).containsExactly(adminPrivateBoard.getBoardId(), publicBoard.getBoardId());
        assertThat(boardIds).doesNotContain(secretOnlyPublicBoard.getBoardId());
    }

    private Board persistBoard(String boardName, String boardUrl, int sortOrder, boolean isActive, boolean isPublic) {
        return persistBoard(boardName, boardUrl, sortOrder, isActive, isPublic, creator);
    }

    private Board persistBoard(String boardName, String boardUrl, int sortOrder, boolean isActive, boolean isPublic,
            User boardCreator) {
        Board board = Board.builder()
                .boardName(boardName)
                .boardUrl(boardUrl)
                .creator(boardCreator)
                .sortOrder(sortOrder)
                .isPublic(isPublic)
                .build();
        if (!isActive) {
            board.deactivate();
        }
        entityManager.persist(board);
        return board;
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

    private void persistPosts(Board board, int count) {
        for (int index = 0; index < count; index++) {
            entityManager.persist(Post.builder()
                    .title(board.getBoardName() + " Post " + index)
                    .contents("contents")
                    .user(creator)
                    .board(board)
                    .isNotice(false)
                    .isNsfw(false)
                    .isSpoiler(false)
                    .isSecret(false)
                    .build());
        }
    }

    private void persistDeletedPosts(Board board, int count) {
        for (int index = 0; index < count; index++) {
            Post post = Post.builder()
                    .title(board.getBoardName() + " Deleted Post " + index)
                    .contents("contents")
                    .user(creator)
                    .board(board)
                    .isNotice(false)
                    .isNsfw(false)
                    .isSpoiler(false)
                    .isSecret(false)
                    .build();
            post.deletePost();
            entityManager.persist(post);
        }
    }

    private void persistSecretPosts(Board board, int count) {
        for (int index = 0; index < count; index++) {
            entityManager.persist(Post.builder()
                    .title(board.getBoardName() + " Secret Post " + index)
                    .contents("contents")
                    .user(creator)
                    .board(board)
                    .isNotice(false)
                    .isNsfw(false)
                    .isSpoiler(false)
                    .isSecret(true)
                    .build());
        }
    }
}
