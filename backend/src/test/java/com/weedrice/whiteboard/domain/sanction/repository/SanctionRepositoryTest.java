package com.weedrice.whiteboard.domain.sanction.repository;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class SanctionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SanctionRepository sanctionRepository;

    @Test
    @DisplayName("활성 MUTE 제재를 타입 목록으로 조회한다")
    void existsActiveTypeIn_returnsTrueForActiveMute() {
        TestSanctionActors actors = persistActors();
        LocalDateTime now = LocalDateTime.now();
        persistSanction(actors, "MUTE", now.minusMinutes(1), now.plusDays(1));
        persistSanction(actors, "WARNING", now.minusMinutes(1), null);

        boolean exists = sanctionRepository.existsActiveTypeIn(actors.targetUser(), Set.of("MUTE"), now);

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("만료되었거나 아직 시작하지 않은 제재는 활성 타입 조회에서 제외한다")
    void existsActiveTypeIn_excludesExpiredAndFutureSanctions() {
        TestSanctionActors actors = persistActors();
        LocalDateTime now = LocalDateTime.now();
        persistSanction(actors, "MUTE", now.minusDays(2), now.minusDays(1));
        persistSanction(actors, "MUTE", now.plusMinutes(1), now.plusDays(1));

        boolean exists = sanctionRepository.existsActiveTypeIn(actors.targetUser(), Set.of("MUTE"), now);

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("활성 BAN 조회는 영구 제재를 포함한다")
    void existsActiveBan_includesPermanentBan() {
        TestSanctionActors actors = persistActors();
        LocalDateTime now = LocalDateTime.now();
        persistSanction(actors, "BAN", now.minusMinutes(1), null);

        boolean exists = sanctionRepository.existsActiveBan(actors.targetUser(), now);

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("active restriction list includes only active requested types")
    void findActiveTypesInOrderByCreatedAtDescSanctionIdDesc_returnsActiveRequestedTypesOnly() {
        TestSanctionActors actors = persistActors();
        LocalDateTime now = LocalDateTime.now();
        persistSanction(actors, "BAN", now.minusMinutes(2), now.plusDays(1));
        persistSanction(actors, "MUTE", now.minusMinutes(1), null);
        persistSanction(actors, "WARNING", now.minusMinutes(1), null);
        persistSanction(actors, "BAN", now.minusDays(2), now.minusDays(1));
        persistSanction(actors, "MUTE", now.plusMinutes(1), now.plusDays(1));

        var restrictions = sanctionRepository.findActiveTypesInOrderByCreatedAtDescSanctionIdDesc(
                actors.targetUser(),
                Set.of("BAN", "MUTE"),
                now);

        assertThat(restrictions)
                .extracting(Sanction::getType)
                .containsExactly("MUTE", "BAN");
    }

    private void persistSanction(TestSanctionActors actors, String type, LocalDateTime startDate,
                                 LocalDateTime endDate) {
        entityManager.persist(Sanction.builder()
                .targetUser(actors.targetUser())
                .admin(actors.admin())
                .processorUser(actors.admin().getUser())
                .type(type)
                .remark(type + " test")
                .startDate(startDate)
                .endDate(endDate)
                .build());
        entityManager.flush();
        entityManager.clear();
    }

    private TestSanctionActors persistActors() {
        User boardCreator = persistUser("creator", "creator@test.com", "노드생성자");
        User adminUser = persistUser("admin", "admin@test.com", "관리자");
        User targetUser = persistUser("target", "target@test.com", "대상자");

        Board board = Board.builder()
                .boardName("free")
                .boardUrl("free")
                .creator(boardCreator)
                .build();
        entityManager.persist(board);

        Admin admin = Admin.builder()
                .user(adminUser)
                .board(board)
                .role(Role.BOARD_ADMIN)
                .build();
        entityManager.persist(admin);
        entityManager.flush();

        return new TestSanctionActors(targetUser, admin);
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

    private record TestSanctionActors(User targetUser, Admin admin) {
    }
}
