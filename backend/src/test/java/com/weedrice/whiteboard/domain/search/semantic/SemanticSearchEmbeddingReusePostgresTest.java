package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = "spring.flyway.postgresql.transactional-lock=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("postgres-smoke")
@EnabledIfEnvironmentVariable(named = "POSTGRES_SMOKE_TEST", matches = "true")
@Import(QuerydslConfig.class)
class SemanticSearchEmbeddingReusePostgresTest {
    private static final String MODEL = "text-embedding-3-small";
    @Autowired private TestEntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PostRepository posts;
    @Autowired private CommentRepository comments;
    @Autowired private BoardRepository boards;
    @Autowired private UserRepository users;
    private SemanticSearchEmbeddingRepository embeddings;
    private SemanticSearchIndexTransactionService transaction;
    private SemanticSearchIndexService indexing;
    private EmbeddingClient provider;
    private Post post;
    private Comment comment;
    private Board previousBoard;
    private SemanticSearchPostIndexPayload payload;
    private float[] vector;

    @BeforeEach
    void setUp() {
        embeddings = new SemanticSearchEmbeddingRepository(jdbc, Clock.systemUTC());
        transaction = new SemanticSearchIndexTransactionService(posts, comments, boards, users,
                new SemanticSearchTextBuilder(), embeddings);
        provider = mock(EmbeddingClient.class);
        when(provider.model()).thenReturn(MODEL);
        indexing = new SemanticSearchIndexService(provider, transaction);
        User user = entityManager.persist(User.builder().loginId("embedding-reuse-pg")
                .email("embedding-reuse-pg@test.example").password("test-password").displayName("reuse").build());
        Board board = entityManager.persist(Board.builder().boardName("Reuse")
                .boardUrl("embedding-reuse-pg").description("reuse test").creator(user).build());
        previousBoard = entityManager.persist(Board.builder().boardName("Previous")
                .boardUrl("embedding-reuse-previous-pg").description("reuse test").creator(user).build());
        post = entityManager.persist(Post.builder().board(board).user(user).title("title").contents("body").build());
        comment = entityManager.persist(Comment.builder().post(post).user(user).depth(0).content("comment").build());
        entityManager.flush();
        payload = transaction.loadPostIndexPayload(post.getPostId());
        vector = new float[1536];
        vector[0] = 1;
    }

    @Test
    void reusesLiveVectorAndRefreshesMetadataWithoutProviderCall() {
        seedPost(payload.embeddingHash(), MODEL);
        String before = storedVector("POST", post.getPostId());

        indexing.upsertPost(post.getPostId());

        verify(provider, never()).embed(anyString());
        assertThat(storedVector("POST", post.getPostId())).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT board_id FROM semantic_search_embeddings WHERE content_type='POST' AND content_id=?",
                Long.class, post.getPostId())).isEqualTo(post.getBoard().getBoardId());
    }

    @Test
    void reactivatesMatchingTombstoneWithoutProviderCall() {
        seedPost(payload.embeddingHash(), MODEL);
        embeddings.tombstone("POST", post.getPostId());

        indexing.upsertPost(post.getPostId());

        verify(provider, never()).embed(anyString());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM semantic_search_embeddings WHERE content_type='POST' AND content_id=?",
                Boolean.class, post.getPostId())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"hash", "model"})
    void differentHashOrModelRequiresNewEmbedding(String changed) {
        seedPost(changed.equals("hash") ? "different" : payload.embeddingHash(), changed.equals("model") ? "old-model" : MODEL);
        float[] replacement = new float[1536];
        replacement[1] = 1;
        when(provider.embed(payload.embeddingText())).thenReturn(replacement);

        indexing.upsertPost(post.getPostId());

        verify(provider).embed(payload.embeddingText());
        assertThat(storedVector("POST", post.getPostId())).startsWith("[0,1,");
        assertThat(jdbc.queryForObject("SELECT embedding_model FROM semantic_search_embeddings WHERE content_type='POST' AND content_id=?",
                String.class, post.getPostId())).isEqualTo(MODEL);
    }

    @Test
    void reusesMatchingCommentVector() {
        var commentPayload = transaction.loadCommentIndexPayload(comment.getCommentId());
        embeddings.upsertComment(comment.getCommentId(), post.getPostId(), previousBoard.getBoardId(), post.getUserId(), null,
                commentPayload.embeddingText(), commentPayload.embeddingHash(), MODEL, vector);

        indexing.upsertComment(comment.getCommentId());

        verify(provider, never()).embed(anyString());
        assertThat(jdbc.queryForObject("SELECT board_id FROM semantic_search_embeddings WHERE content_type='COMMENT' AND content_id=?",
                Long.class, comment.getCommentId())).isEqualTo(post.getBoard().getBoardId());
    }

    private void seedPost(String hash, String model) {
        embeddings.upsertPost(post.getPostId(), previousBoard.getBoardId(), post.getUserId(), null,
                payload.embeddingText(), hash, model, vector);
    }

    private String storedVector(String type, Long id) {
        return jdbc.queryForObject("SELECT embedding::text FROM semantic_search_embeddings WHERE content_type=? AND content_id=?",
                String.class, type, id);
    }
}
