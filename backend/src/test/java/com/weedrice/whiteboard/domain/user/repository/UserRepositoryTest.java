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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private User user1;
    private User user3;

    @BeforeEach
    void setUp() {
        user1 = User.builder().loginId("testuser1").displayName("Apple User").email("test1@test.com").password("pass").build();
        User user2 = User.builder().loginId("testuser2").displayName("Banana User").email("test2@test.com").password("pass").build();
        user3 = User.builder().loginId("another").displayName("Apple Another").email("test3@test.com").password("pass").build();

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

    @Test
    @DisplayName("공개 사용자 검색은 ACTIVE 이고 삭제되지 않은 사용자만 노출한다")
    void searchUsersVisibleTo_filtersSuspendedAndDeletedUsers() {
        User suspendedUser = User.builder()
                .loginId("suspended")
                .displayName("Apple Suspended")
                .email("suspended@test.com")
                .password("pass")
                .build();
        suspendedUser.suspend();

        User deletedUser = User.builder()
                .loginId("deleted")
                .displayName("Apple Deleted")
                .email("deleted@test.com")
                .password("pass")
                .build();
        deletedUser.delete();

        entityManager.persist(suspendedUser);
        entityManager.persist(deletedUser);
        entityManager.flush();

        Page<User> result = userRepository.searchUsersVisibleTo("Apple", null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(User::getDisplayName)
                .containsExactlyInAnyOrder("Apple User", "Apple Another");
    }

    @Test
    @DisplayName("공개 사용자 검색은 차단 사용자도 제외한다")
    void searchUsersVisibleTo_excludesBlockedUsers() {
        Page<User> result = userRepository.searchUsersVisibleTo("Apple", List.of(user1.getUserId()), PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(User::getDisplayName)
                .containsExactly(user3.getDisplayName());
    }
}
