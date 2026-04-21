package com.weedrice.whiteboard.domain.board.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.springframework.context.annotation.Import;

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
        Board privateBoard = persistBoard("Private Top", "private-top", 1, true, false);
        Board inactiveBoard = persistBoard("Inactive Top", "inactive-top", 2, false, true);
        Board inquiryBoard = persistBoard("Inquiry Top", "Inquiry", 3, true, true);

        persistPosts(visibleTop, 4);
        persistPosts(visibleTieFirst, 2);
        persistPosts(visibleTieSecond, 2);
        persistPosts(privateBoard, 6);
        persistPosts(inactiveBoard, 5);
        persistPosts(inquiryBoard, 7);

        entityManager.flush();
        entityManager.clear();

        var boards = boardRepository.findTopPublicBoardsByPostCount(PageRequest.of(0, 10));

        assertThat(boards).extracting(Board::getBoardName)
                .containsExactly("Visible Top", "Visible Tie First", "Visible Tie Second");
    }

    private Board persistBoard(String boardName, String boardUrl, int sortOrder, boolean isActive, boolean isPublic) {
        Board board = Board.builder()
                .boardName(boardName)
                .boardUrl(boardUrl)
                .creator(creator)
                .sortOrder(sortOrder)
                .isPublic(isPublic)
                .build();
        if (!isActive) {
            board.deactivate();
        }
        entityManager.persist(board);
        return board;
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
}
