package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserBlock;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class UserBlockRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserBlockRepository userBlockRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private User user1;
    private User user2;
    private User user3;

    @BeforeEach
    void setUp() {
        user1 = User.builder().loginId("user1").displayName("User One").email("user1@test.com").password("pass").build();
        user2 = User.builder().loginId("user2").displayName("User Two").email("user2@test.com").password("pass").build();
        user3 = User.builder().loginId("user3").displayName("User Three").email("user3@test.com").password("pass").build();

        entityManager.persist(user1);
        entityManager.persist(user2);
        entityManager.persist(user3);

        entityManager.persist(UserBlock.builder().user(user1).target(user2).build());
        entityManager.persist(UserBlock.builder().user(user1).target(user3).build());
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("차단 관계 존재 여부 확인")
    void existsByUserAndTarget_returnsTrue_whenBlockExists() {
        boolean exists = userBlockRepository.existsByUserAndTarget(user1, user2);

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("차단 관계가 없으면 false를 반환한다")
    void existsByUserAndTarget_returnsFalse_whenBlockDoesNotExist() {
        boolean exists = userBlockRepository.existsByUserAndTarget(user2, user1);

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("ID 기반 차단 여부를 확인한다")
    void existsByUserIdAndTargetUserId_returnsTrue_whenBlockExists() {
        boolean exists = userBlockRepository.existsByUser_UserIdAndTarget_UserId(user1.getUserId(), user2.getUserId());

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("양방향 차단 여부를 확인한다")
    void existsEitherDirection_returnsExpectedValue() {
        boolean blocked = userBlockRepository.existsEitherDirection(user1.getUserId(), user2.getUserId());
        boolean notBlocked = userBlockRepository.existsEitherDirection(user2.getUserId(), user3.getUserId());

        assertThat(blocked).isTrue();
        assertThat(notBlocked).isFalse();
    }

    @Test
    @DisplayName("차단 관계 단건 조회 성공")
    void findByUserAndTarget_returnsUserBlock_whenBlockExists() {
        Optional<UserBlock> foundBlock = userBlockRepository.findByUserAndTarget(user1, user2);

        assertThat(foundBlock).isPresent();
        assertThat(foundBlock.get().getUser().getUserId()).isEqualTo(user1.getUserId());
        assertThat(foundBlock.get().getTarget().getUserId()).isEqualTo(user2.getUserId());
    }

    @Test
    @DisplayName("차단 목록 전용 조회는 target을 함께 로드한다")
    void findPageByUserWithTarget_fetchesTarget() {
        Page<UserBlock> result = userBlockRepository.findPageByUserWithTarget(user1, PageRequest.of(0, 10));
        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(persistenceUnitUtil.isLoaded(result.getContent().getFirst(), "target")).isTrue();
        assertThat(result.getContent())
                .extracting(block -> block.getTarget().getUserId())
                .containsExactlyInAnyOrder(user2.getUserId(), user3.getUserId());
    }

    @Test
    @DisplayName("차단 사용자 ID 조회용 목록도 target을 함께 로드한다")
    void findByUserWithTarget_fetchesTarget() {
        List<UserBlock> result = userBlockRepository.findByUserWithTarget(user1);
        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();

        assertThat(result).hasSize(2);
        assertThat(persistenceUnitUtil.isLoaded(result.getFirst(), "target")).isTrue();
        assertThat(result)
                .extracting(block -> block.getTarget().getUserId())
                .containsExactlyInAnyOrder(user2.getUserId(), user3.getUserId());
    }
}
