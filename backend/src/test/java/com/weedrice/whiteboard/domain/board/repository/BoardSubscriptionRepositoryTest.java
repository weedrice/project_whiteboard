package com.weedrice.whiteboard.domain.board.repository;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.user.entity.Role;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class BoardSubscriptionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private BoardSubscriptionRepository boardSubscriptionRepository;

    private User viewer;
    private User superAdmin;
    private User owner;
    private Board adminOnlyBoard;
    private Board ownPrivateBoard;
    private Board publicBoard;
    private Board hiddenBoard;
    private Board inactiveBoard;

    @BeforeEach
    void setUp() {
        viewer = persistUser("viewer", "viewer@test.com", "조회자");
        superAdmin = User.builder()
                .loginId("super-admin")
                .email("super-admin@test.com")
                .password("password")
                .displayName("슈퍼관리자")
                .build();
        superAdmin.grantSuperAdminRole();
        entityManager.persist(superAdmin);
        owner = persistUser("owner", "owner@test.com", "노드주인");

        adminOnlyBoard = persistBoard("Admin Only", "admin-only", owner, false, false);
        ownPrivateBoard = persistBoard("Own Private", "own-private", viewer, false, false);
        publicBoard = persistBoard("Public", "public", owner, true, true);
        hiddenBoard = persistBoard("Hidden", "hidden", owner, true, false);
        inactiveBoard = persistBoard("Inactive", "inactive", owner, false, true);

        entityManager.persist(Admin.builder()
                .user(viewer)
                .board(adminOnlyBoard)
                .role(Role.BOARD_ADMIN)
                .build());

        persistSubscription(viewer, adminOnlyBoard, 10);
        persistSubscription(viewer, ownPrivateBoard, 20);
        persistSubscription(viewer, publicBoard, 30);
        persistSubscription(viewer, hiddenBoard, 40);
        persistSubscription(viewer, inactiveBoard, 50);

        persistSubscription(superAdmin, hiddenBoard, 5);
        persistSubscription(superAdmin, inactiveBoard, 15);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("일반 사용자는 읽을 수 있는 구독만 정렬 순서대로 페이지 조회한다")
    void findVisibleByUserOrderBySortOrderAsc_filtersUnreadableBoards() {
        Page<BoardSubscription> result = boardSubscriptionRepository.findVisibleByUserOrderBySortOrderAsc(
                viewer,
                false,
                PageRequest.of(0, 10));
        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(subscription -> subscription.getBoard().getBoardUrl())
                .containsExactly("admin-only", "public");
        assertThat(persistenceUnitUtil.isLoaded(result.getContent().get(0), "board")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(result.getContent().get(0).getBoard(), "creator")).isTrue();
    }

    @Test
    @DisplayName("슈퍼 관리자는 비공개 또는 비활성 노드 구독도 그대로 조회한다")
    void findVisibleByUserOrderBySortOrderAsc_includesAllSubscribedBoardsForSuperAdmin() {
        Page<BoardSubscription> result = boardSubscriptionRepository.findVisibleByUserOrderBySortOrderAsc(
                superAdmin,
                true,
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(subscription -> subscription.getBoard().getBoardUrl())
                .containsExactly("hidden", "inactive");
    }

    @Test
    @DisplayName("findBoardUrlsByUserIdAndBoardIdIn returns only subscribed board URLs")
    void findBoardUrlsByUserIdAndBoardIdIn_success() {
        List<String> boardUrls = boardSubscriptionRepository.findBoardUrlsByUserIdAndBoardIdIn(
                viewer.getUserId(),
                List.of(publicBoard.getBoardId(), inactiveBoard.getBoardId(), -1L));

        assertThat(boardUrls).containsExactlyInAnyOrder("public", "inactive");
    }

    @Test
    @DisplayName("관리자 후보 조회는 해당 노드의 활성 구독자만 반환한다")
    void findManagerCandidatesByBoard_returnsOnlyActiveSubscribers() {
        Board candidateBoard = persistBoard("Candidates", "candidates", owner, true, true);
        User alpha = persistUser("alpha", "alpha@test.com", "Alpha");
        User bravo = persistUser("bravo", "bravo@test.com", "Bravo");
        User suspended = persistUser("charlie", "charlie@test.com", "Charlie");
        User deleted = persistUser("delta", "delta@test.com", "Delta");
        persistUser("echo", "echo@test.com", "Echo");

        suspended.suspend();
        deleted.delete(java.time.LocalDateTime.of(2026, 7, 7, 12, 0));
        persistSubscription(bravo, candidateBoard, 1);
        persistSubscription(alpha, candidateBoard, 1);
        persistSubscription(suspended, candidateBoard, 1);
        persistSubscription(deleted, candidateBoard, 1);
        entityManager.flush();
        entityManager.clear();

        Board board = entityManager.find(Board.class, candidateBoard.getBoardId());

        Page<BoardSubscription> result = boardSubscriptionRepository.findManagerCandidatesByBoard(
                board,
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(subscription -> subscription.getUser().getLoginId())
                .containsExactly("alpha", "bravo");
    }

    @Test
    @DisplayName("관리자 후보 조회는 loginId와 displayName 검색 및 pagination을 지원한다")
    void findManagerCandidatesByBoard_supportsKeywordAndPagination() {
        Board candidateBoard = persistBoard("Paged Candidates", "paged-candidates", owner, true, true);
        User alpha = persistUser("alpha-page", "alpha-page@test.com", "First");
        User beta = persistUser("beta-page", "beta-page@test.com", "Needle User");
        User gamma = persistUser("gamma-page", "gamma-page@test.com", "Needle Admin");

        persistSubscription(gamma, candidateBoard, 1);
        persistSubscription(alpha, candidateBoard, 1);
        persistSubscription(beta, candidateBoard, 1);
        entityManager.flush();
        entityManager.clear();

        Board board = entityManager.find(Board.class, candidateBoard.getBoardId());

        Page<BoardSubscription> loginSearch = boardSubscriptionRepository.findManagerCandidatesByBoardAndKeyword(
                board,
                "%alpha%",
                PageRequest.of(0, 10));
        Page<BoardSubscription> displayNameSearch = boardSubscriptionRepository.findManagerCandidatesByBoardAndKeyword(
                board,
                "%needle%",
                PageRequest.of(0, 10));
        Page<BoardSubscription> firstPage = boardSubscriptionRepository.findManagerCandidatesByBoard(
                board,
                PageRequest.of(0, 2));

        assertThat(loginSearch.getContent())
                .extracting(subscription -> subscription.getUser().getLoginId())
                .containsExactly("alpha-page");
        assertThat(displayNameSearch.getTotalElements()).isEqualTo(2);
        assertThat(displayNameSearch.getContent())
                .extracting(subscription -> subscription.getUser().getLoginId())
                .containsExactly("beta-page", "gamma-page");
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent())
                .extracting(subscription -> subscription.getUser().getLoginId())
                .containsExactly("alpha-page", "beta-page");
        assertThat(firstPage.hasNext()).isTrue();
    }

    @Test
    @DisplayName("manager candidate keyword treats escaped LIKE wildcard as literal")
    void findManagerCandidatesByBoardAndKeyword_escapesLikeWildcard() {
        Board candidateBoard = persistBoard("Wildcard Candidates", "wildcard-candidates", owner, true, true);
        User literalMatch = persistUser("literal%match", "literal-percent@test.com", "Literal Percent");
        User wildcardOnlyMatch = persistUser("literalXmatch", "literal-x@test.com", "Wildcard Only");

        persistSubscription(literalMatch, candidateBoard, 1);
        persistSubscription(wildcardOnlyMatch, candidateBoard, 1);
        entityManager.flush();
        entityManager.clear();

        Board board = entityManager.find(Board.class, candidateBoard.getBoardId());

        Page<BoardSubscription> result = boardSubscriptionRepository.findManagerCandidatesByBoardAndKeyword(
                board,
                "%literal!%match%",
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent())
                .extracting(subscription -> subscription.getUser().getLoginId())
                .containsExactly("literal%match");
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

    private Board persistBoard(String boardName, String boardUrl, User creator, boolean isActive, boolean isPublic) {
        Board board = Board.builder()
                .boardName(boardName)
                .boardUrl(boardUrl)
                .creator(creator)
                .isPublic(isPublic)
                .build();
        if (!isActive) {
            board.deactivate();
        }
        entityManager.persist(board);
        return board;
    }

    private void persistSubscription(User user, Board board, int sortOrder) {
        entityManager.persist(BoardSubscription.builder()
                .user(user)
                .board(board)
                .role("MEMBER")
                .sortOrder(sortOrder)
                .build());
    }
}
