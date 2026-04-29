package com.weedrice.whiteboard.domain.admin.repository;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.Role;
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
class AdminRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private AdminRepository adminRepository;

    private Admin newerAdmin;
    private Admin olderAdmin;

    @BeforeEach
    void setUp() {
        User creator = User.builder()
                .loginId("creator")
                .email("creator@test.com")
                .password("password")
                .displayName("게시판생성자")
                .build();
        User adminUser1 = User.builder()
                .loginId("admin1")
                .email("admin1@test.com")
                .password("password")
                .displayName("관리자1")
                .build();
        User adminUser2 = User.builder()
                .loginId("admin2")
                .email("admin2@test.com")
                .password("password")
                .displayName("관리자2")
                .build();
        entityManager.persist(creator);
        entityManager.persist(adminUser1);
        entityManager.persist(adminUser2);

        Board board = Board.builder()
                .boardName("free")
                .boardUrl("free")
                .creator(creator)
                .build();
        entityManager.persist(board);

        olderAdmin = Admin.builder()
                .user(adminUser1)
                .board(board)
                .role(Role.BOARD_ADMIN)
                .build();
        newerAdmin = Admin.builder()
                .user(adminUser2)
                .board(board)
                .role(Role.BOARD_ADMIN)
                .build();

        entityManager.persist(olderAdmin);
        entityManager.persist(newerAdmin);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("관리자 목록 페이지 조회 시 user와 board를 함께 로드하고 최신순으로 반환한다")
    void findAllByOrderByAdminIdDesc_fetchesUserAndBoard() {
        Page<Admin> result = adminRepository.findAllByOrderByAdminIdDesc(PageRequest.of(0, 20));
        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(Admin::getAdminId)
                .isSortedAccordingTo((left, right) -> Long.compare(right, left));
        assertThat(persistenceUnitUtil.isLoaded(result.getContent().get(0), "user")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(result.getContent().get(0), "board")).isTrue();
        assertThat(result.getContent().get(0).getUser().getDisplayName()).isEqualTo("관리자2");
        assertThat(result.getContent().get(0).getBoard().getBoardName()).isEqualTo("free");
    }

    @Test
    @DisplayName("여러 활성 관리자 권한이 있어도 사용자 활성 관리자 존재 여부를 조회한다")
    void existsByUserAndIsActive_multipleActiveRows_returnsTrue() {
        User multiAdminUser = User.builder()
                .loginId("multi-admin")
                .email("multi-admin@test.com")
                .password("password")
                .displayName("multi-admin")
                .build();
        User creator = User.builder()
                .loginId("other-creator")
                .email("other-creator@test.com")
                .password("password")
                .displayName("other-creator")
                .build();
        entityManager.persist(multiAdminUser);
        entityManager.persist(creator);

        Board firstBoard = Board.builder()
                .boardName("first")
                .boardUrl("first")
                .creator(creator)
                .build();
        Board secondBoard = Board.builder()
                .boardName("second")
                .boardUrl("second")
                .creator(creator)
                .build();
        entityManager.persist(firstBoard);
        entityManager.persist(secondBoard);

        entityManager.persist(Admin.builder()
                .user(multiAdminUser)
                .board(firstBoard)
                .role(Role.BOARD_ADMIN)
                .build());
        entityManager.persist(Admin.builder()
                .user(multiAdminUser)
                .board(secondBoard)
                .role(Role.BOARD_ADMIN)
                .build());
        entityManager.flush();
        entityManager.clear();

        assertThat(adminRepository.existsByUserAndIsActive(multiAdminUser, true)).isTrue();
    }
}
