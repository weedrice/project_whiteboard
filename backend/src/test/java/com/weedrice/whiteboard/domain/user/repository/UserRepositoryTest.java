package com.weedrice.whiteboard.domain.user.repository;

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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        User user1 = User.builder().loginId("testuser1").displayName("Apple User").email("test1@test.com").password("pass").build();
        User user2 = User.builder().loginId("testuser2").displayName("Banana User").email("test2@test.com").password("pass").build();
        User user3 = User.builder().loginId("another").displayName("Apple Another").email("test3@test.com").password("pass").build();

        entityManager.persist(user1);
        entityManager.persist(user2);
        entityManager.persist(user3);
        entityManager.flush();
    }

    @Test
    @DisplayName("사용자 검색 성공 - 로그인 ID로 검색")
    void searchUsers_byLoginId() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<User> result = userRepository.searchUsers("testuser", pageRequest);

        // then
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(User::getLoginId).containsExactlyInAnyOrder("testuser1", "testuser2");
    }

    @Test
    @DisplayName("사용자 검색 성공 - 닉네임으로 검색")
    void searchUsers_byDisplayName() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<User> result = userRepository.searchUsers("Apple", pageRequest);

        // then
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(User::getDisplayName).containsExactlyInAnyOrder("Apple User", "Apple Another");
    }

    @Test
    @DisplayName("사용자 검색 - 결과 없음")
    void searchUsers_noResult() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<User> result = userRepository.searchUsers("nonexistent", pageRequest);

        // then
        assertThat(result.getTotalElements()).isZero();
    }
}
