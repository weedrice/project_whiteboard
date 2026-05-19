package com.weedrice.whiteboard.domain.admin.repository;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.entity.IpBlock;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.Role;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class IpBlockRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private IpBlockRepository ipBlockRepository;

    private Admin admin;

    @BeforeEach
    void setUp() {
        User boardOwner = User.builder()
                .loginId("owner")
                .email("owner@test.com")
                .password("password")
                .displayName("보드 소유자")
                .build();
        User adminUser = User.builder()
                .loginId("admin")
                .email("admin@test.com")
                .password("password")
                .displayName("관리자")
                .build();
        entityManager.persist(boardOwner);
        entityManager.persist(adminUser);

        Board board = Board.builder()
                .boardName("test-board")
                .boardUrl("test-board")
                .creator(boardOwner)
                .build();
        entityManager.persist(board);

        admin = Admin.builder()
                .user(adminUser)
                .board(board)
                .role(Role.BOARD_ADMIN)
                .build();
        entityManager.persist(admin);
    }

    @Test
    @DisplayName("다른 IP의 영구 차단이 있어도 대상 IP만 활성 차단으로 조회한다")
    void findActiveByIpAddress_filtersByTargetIp() {
        entityManager.persist(IpBlock.builder()
                .ipAddress("10.0.0.1")
                .admin(admin)
                .processorUser(admin.getUser())
                .reason("other")
                .startDate(LocalDateTime.now().minusDays(2))
                .endDate(null)
                .build());
        entityManager.persist(IpBlock.builder()
                .ipAddress("10.0.0.2")
                .admin(admin)
                .processorUser(admin.getUser())
                .reason("expired")
                .startDate(LocalDateTime.now().minusDays(3))
                .endDate(LocalDateTime.now().minusDays(1))
                .build());
        entityManager.flush();

        assertThat(ipBlockRepository.findActiveByIpAddress("10.0.0.2", LocalDateTime.now())).isEmpty();
        assertThat(ipBlockRepository.findActiveByIpAddress("10.0.0.1", LocalDateTime.now())).isPresent();
    }

    @Test
    @DisplayName("활성 차단 목록 페이지 조회는 만료된 이력을 제외한다")
    void findActiveBlocks_returnsOnlyActiveBlocks() {
        entityManager.persist(IpBlock.builder()
                .ipAddress("10.0.0.1")
                .admin(admin)
                .processorUser(admin.getUser())
                .reason("active")
                .startDate(LocalDateTime.now().minusHours(2))
                .endDate(LocalDateTime.now().plusDays(1))
                .build());
        entityManager.persist(IpBlock.builder()
                .ipAddress("10.0.0.2")
                .admin(admin)
                .processorUser(admin.getUser())
                .reason("expired")
                .startDate(LocalDateTime.now().minusDays(2))
                .endDate(LocalDateTime.now().minusHours(1))
                .build());
        entityManager.flush();
        entityManager.clear();

        Page<IpBlock> result = ipBlockRepository.findActiveBlocks(LocalDateTime.now(), PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(IpBlock::getIpAddress).containsExactly("10.0.0.1");
        assertThat(result.getContent().get(0).getAdmin().getUser().getDisplayName()).isEqualTo("관리자");
    }
}
