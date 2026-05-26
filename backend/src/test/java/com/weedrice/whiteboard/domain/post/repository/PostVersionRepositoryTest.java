package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.dto.PostVersionResponse;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostVersion;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class PostVersionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PostVersionRepository postVersionRepository;

    private User user;
    private Post post;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("author")
                .email("author@test.com")
                .password("password")
                .displayName("author")
                .build();
        entityManager.persist(user);

        Board board = Board.builder()
                .boardName("free")
                .boardUrl("free")
                .creator(user)
                .build();
        entityManager.persist(board);

        post = Post.builder()
                .board(board)
                .user(user)
                .title("Current title")
                .contents("contents")
                .isNotice(false)
                .isNsfw(false)
                .isSpoiler(false)
                .isSecret(false)
                .build();
        entityManager.persist(post);
        entityManager.flush();
    }

    @Test
    @DisplayName("findVersionResponsesByPostId returns DTOs ordered by createdAt desc with title fallback")
    void findVersionResponsesByPostId_returnsProjectedResponses() {
        PostVersion olderVersion = persistVersion("MODIFY", "Previous title", "older contents");
        PostVersion newerVersion = persistVersion("DELETE", null, "newer contents");
        entityManager.flush();

        LocalDateTime baseTime = LocalDateTime.of(2026, 5, 26, 16, 0);
        updateCreatedAt(olderVersion, baseTime);
        updateCreatedAt(newerVersion, baseTime.plusMinutes(1));
        entityManager.flush();
        entityManager.clear();

        List<PostVersionResponse> responses = postVersionRepository.findVersionResponsesByPostId(post.getPostId());

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(PostVersionResponse::getVersionId)
                .containsExactly(newerVersion.getHistoryId(), olderVersion.getHistoryId());
        assertThat(responses).extracting(PostVersionResponse::getActionType)
                .containsExactly("DELETE", "MODIFY");
        assertThat(responses).extracting(PostVersionResponse::getTitle)
                .containsExactly("Current title", "Previous title");
        assertThat(responses).extracting(PostVersionResponse::getCreatedAt)
                .containsExactly(baseTime.plusMinutes(1), baseTime);
    }

    private PostVersion persistVersion(String versionType, String originalTitle, String originalContents) {
        PostVersion version = PostVersion.builder()
                .post(post)
                .modifier(user)
                .versionType(versionType)
                .originalTitle(originalTitle)
                .originalContents(originalContents)
                .build();
        entityManager.persist(version);
        return version;
    }

    private void updateCreatedAt(PostVersion version, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE post_versions SET created_at = :createdAt WHERE history_id = :historyId")
                .setParameter("createdAt", createdAt)
                .setParameter("historyId", version.getHistoryId())
                .executeUpdate();
    }
}
