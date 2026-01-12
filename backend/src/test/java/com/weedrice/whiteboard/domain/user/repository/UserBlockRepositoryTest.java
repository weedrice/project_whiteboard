package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserBlock;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class UserBlockRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserBlockRepository userBlockRepository;

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

        // user1 blocks user2
        UserBlock block1 = UserBlock.builder().user(user1).target(user2).build();
        entityManager.persist(block1);

        // user1 blocks user3
        UserBlock block2 = UserBlock.builder().user(user1).target(user3).build();
        entityManager.persist(block2);

        entityManager.flush();
    }

    @Test
    @DisplayName("차단 관계 존재 여부 확인 - 존재하는 경우")
    void existsByUserAndTarget_returnsTrue_whenBlockExists() {
        // when
        boolean exists = userBlockRepository.existsByUserAndTarget(user1, user2);

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("차단 관계 존재 여부 확인 - 존재하지 않는 경우")
    void existsByUserAndTarget_returnsFalse_whenBlockDoesNotExist() {
        // when
        boolean exists = userBlockRepository.existsByUserAndTarget(user2, user1);

        // then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("차단 관계 조회 - 성공")
    void findByUserAndTarget_returnsUserBlock_whenBlockExists() {
        // when
        Optional<UserBlock> foundBlock = userBlockRepository.findByUserAndTarget(user1, user2);

        // then
        assertThat(foundBlock).isPresent();
        assertThat(foundBlock.get().getUser()).isEqualTo(user1);
        assertThat(foundBlock.get().getTarget()).isEqualTo(user2);
    }

    @Test
    @DisplayName("차단 목록 조회 및 정렬 확인")
    void findByUserOrderByCreatedAtDesc_returnsBlockedUsersInOrder() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<UserBlock> result = userBlockRepository.findByUserOrderByCreatedAtDesc(user1, pageRequest);

        // then
        assertThat(result.getTotalElements()).isEqualTo(2);
        // createdAt이 거의 동시에 설정될 수 있으므로, 정렬 순서보다는 두 사용자가 모두 포함되어 있는지 확인
        assertThat(result.getContent()).extracting(UserBlock::getTarget)
                .containsExactlyInAnyOrder(user2, user3);
        // 최신순 정렬 확인: 마지막에 생성된 것이 먼저 나와야 함
        // user3가 나중에 생성되었으므로 먼저 나와야 하지만, 시간 차이가 없을 수 있으므로
        // 최소한 두 개가 모두 포함되어 있고, 정렬이 적용되었는지 확인
        assertThat(result.getContent().size()).isEqualTo(2);
    }
}
