package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.dto.UserAdminSearchCondition;
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
import org.springframework.data.domain.Sort;

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
    private Board board;

    @BeforeEach
    void setUp() {
        user1 = User.builder().loginId("testuser1").displayName("Apple User").email("test1@test.com").password("pass").build();
        User user2 = User.builder().loginId("testuser2").displayName("Banana User").email("test2@test.com").password("pass").build();
        user3 = User.builder().loginId("another").displayName("Apple Another").email("test3@test.com").password("pass").build();

        entityManager.persist(user1);
        entityManager.persist(user2);
        entityManager.persist(user3);
        board = Board.builder()
                .boardName("free")
                .boardUrl("free")
                .creator(user1)
                .build();
        entityManager.persist(board);
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

    @Test
    @DisplayName("관리자 사용자 검색은 minActivityCount가 있을 때 활동량 집계 경로로 정렬과 total을 유지한다")
    void searchUsersForAdmin_withMinActivityCount_preservesOrderAndTotal() {
        Post user1Post1 = Post.builder().board(board).user(user1).title("p1").contents("c").build();
        Post user1Post2 = Post.builder().board(board).user(user1).title("p2").contents("c").build();
        entityManager.persist(user1Post1);
        entityManager.persist(user1Post2);
        entityManager.persist(Comment.builder().post(user1Post1).user(user1).content("comment").depth(0).build());

        Post user3Post = Post.builder().board(board).user(user3).title("u3").contents("c").build();
        entityManager.persist(user3Post);
        entityManager.persist(Comment.builder().post(user3Post).user(user3).content("comment").depth(0).build());
        entityManager.flush();
        entityManager.clear();

        UserAdminSearchCondition condition = UserAdminSearchCondition.builder()
                .minActivityCount(2L)
                .build();

        Page<User> result = userRepository.searchUsersForAdmin(
                null,
                condition,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "displayName")));

        assertThat(result.getTotalElements()).isEqualTo(2L);
        assertThat(result.getContent())
                .extracting(User::getDisplayName)
                .containsExactly("Apple Another", "Apple User");
    }

    @Test
    @DisplayName("관리자 사용자 검색은 minActivityCount 경로에서도 role 필터 의미를 유지한다")
    void searchUsersForAdmin_withMinActivityCount_preservesRoleFilter() {
        Post user1Post1 = Post.builder().board(board).user(user1).title("p1").contents("c").build();
        Post user1Post2 = Post.builder().board(board).user(user1).title("p2").contents("c").build();
        entityManager.persist(user1Post1);
        entityManager.persist(user1Post2);

        Post user3Post1 = Post.builder().board(board).user(user3).title("u31").contents("c").build();
        Post user3Post2 = Post.builder().board(board).user(user3).title("u32").contents("c").build();
        entityManager.persist(user3Post1);
        entityManager.persist(user3Post2);
        entityManager.persist(Admin.builder().user(user3).board(board).role(Role.BOARD_ADMIN).build());
        entityManager.flush();
        entityManager.clear();

        UserAdminSearchCondition condition = UserAdminSearchCondition.builder()
                .minActivityCount(2L)
                .role(Role.USER)
                .build();

        Page<User> result = userRepository.searchUsersForAdmin(
                null,
                condition,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "displayName")));

        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getContent())
                .extracting(User::getDisplayName)
                .containsExactly("Apple User");
    }
}
