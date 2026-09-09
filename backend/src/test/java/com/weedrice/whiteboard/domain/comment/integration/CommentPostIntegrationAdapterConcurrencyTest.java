package com.weedrice.whiteboard.domain.comment.integration;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.inquiry.legacy.InquiryLegacyWritePolicy;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.post.service.PostAuthorCommandPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.domain.user.service.UserReadableResolver;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(QuerydslConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CommentPostIntegrationAdapterConcurrencyTest {
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private BoardRepository boardRepository;
    @Autowired
    private TransactionTemplate transactions;

    private Long postId;
    private Long boardId;
    private Long userId;

    @BeforeEach
    void setUp() {
        transactions.executeWithoutResult(status -> {
            User user = User.builder().loginId("parent-lock-user").email("parent-lock@example.com")
                    .password("test-password").displayName("Parent lock author").build();
            entityManager.persist(user);
            Board board = Board.builder().boardName("Parent locks").boardUrl("parent-locks")
                    .creator(user).build();
            entityManager.persist(board);
            Post post = Post.builder().title("Post").contents("Contents").user(user).board(board).build();
            entityManager.persist(post);
            userId = user.getUserId();
            boardId = board.getBoardId();
            postId = post.getPostId();
        });
    }

    @AfterEach
    void cleanUp() {
        transactions.executeWithoutResult(status -> {
            entityManager.remove(entityManager.find(Post.class, postId));
            entityManager.remove(entityManager.find(Board.class, boardId));
            entityManager.remove(entityManager.find(User.class, userId));
        });
    }

    @Test
    void observesPostDeletionCommittedBeforeParentLock() {
        assertConcurrentChangeIsObserved(Post::deletePost);
    }

    @Test
    void observesBoardPrivacyCommittedBeforeParentLock() {
        assertConcurrentChangeIsObserved(post -> {
            Board board = post.getBoard();
            board.update(board.getBoardName(), board.getDescription(), board.getIconUrl(), board.getSortOrder(),
                    board.getAllowNsfw(), board.getIsActive(), false);
        });
    }

    @Test
    void observesPostPrivacyCommittedBeforeParentLock() {
        assertConcurrentChangeIsObserved(post -> ReflectionTestUtils.setField(post, "isSecret", true));
    }

    @Test
    void observesPostBlindingCommittedBeforeParentLock() {
        assertConcurrentChangeIsObserved(post -> ReflectionTestUtils.setField(post, "isBlinded", true));
    }

    @Test
    void refreshesPreloadedPostAndBoardAfterPrivacyChange() {
        assertConcurrentChangeIsObserved(post -> {
            Board board = post.getBoard();
            board.update(board.getBoardName(), board.getDescription(), board.getIconUrl(), board.getSortOrder(),
                    board.getAllowNsfw(), board.getIsActive(), false);
        }, true);
    }

    @Test
    void refreshesPreloadedPostAfterDeletion() {
        assertConcurrentChangeIsObserved(Post::deletePost, true);
    }

    private void assertConcurrentChangeIsObserved(Consumer<Post> change) {
        assertConcurrentChangeIsObserved(change, false);
    }

    private void assertConcurrentChangeIsObserved(Consumer<Post> change, boolean preload) {
        TransactionTemplate concurrent = new TransactionTemplate(transactions.getTransactionManager());
        concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        BoardRepository interceptedBoards = mock(BoardRepository.class);
        when(interceptedBoards.findByIdForUpdate(boardId)).thenAnswer(invocation -> {
            // Commit in a separate persistence context after the initial lookup, before acquiring the lock.
            concurrent.executeWithoutResult(status -> change.accept(postRepository.findById(postId).orElseThrow()));
            return boardRepository.findByIdForUpdate(boardId);
        });
        InquiryLegacyWritePolicy legacyPolicy = mock(InquiryLegacyWritePolicy.class);
        PostAccessPolicy accessPolicy = new PostAccessPolicy(
                new BoardAccessPolicy(mock(AdminRepository.class)), legacyPolicy);
        CommentPostIntegrationAdapter adapter = new CommentPostIntegrationAdapter(
                entityManager, postRepository, interceptedBoards, accessPolicy, mock(PostAuthorCommandPolicy.class), legacyPolicy,
                mock(UserReadableResolver.class), mock(UserWritableResolver.class), mock(UserBlockService.class));

        transactions.executeWithoutResult(status -> {
            if (preload) {
                postRepository.findByIdWithRelations(postId).orElseThrow();
            }
            adapter.lockForWrite(postId);
            assertThatThrownBy(() -> adapter.validateReadable(postId, null, null))
                    .isInstanceOfSatisfying(BusinessException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.POST_NOT_FOUND));
        });
    }
}
