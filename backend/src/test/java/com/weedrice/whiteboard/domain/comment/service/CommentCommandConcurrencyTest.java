package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.actor.ActorWritePort;
import com.weedrice.whiteboard.domain.badge.service.BadgeEvaluationService;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.entity.CommentVersion;
import com.weedrice.whiteboard.domain.comment.port.CommentNotificationPort;
import com.weedrice.whiteboard.domain.comment.port.CommentPostPort;
import com.weedrice.whiteboard.domain.comment.port.CommentPostSnapshot;
import com.weedrice.whiteboard.domain.comment.port.CommentUserWritePort;
import com.weedrice.whiteboard.domain.comment.repository.CommentClosureRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentLikeRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentMentionRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentVersionRepository;
import com.weedrice.whiteboard.domain.notification.service.CommentStreamEventDispatcher;
import com.weedrice.whiteboard.domain.point.service.ContentRewardService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchEventPublisher;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.AnonymousReadCacheInvalidator;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(QuerydslConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CommentCommandConcurrencyTest {
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private TransactionTemplate transactions;

    private CommentPostPort postPort;
    private CommentVersionRepository versionRepository;
    private ContentRewardService rewardService;
    private CommentCommandService commandService;
    private Long userId;
    private Long boardId;
    private Long postId;
    private Long commentId;

    @BeforeEach
    void setUp() {
        transactions.executeWithoutResult(status -> {
            User user = User.builder().loginId("comment-lock-user").email("comment-lock@example.com")
                    .password("test-password").displayName("Comment author").build();
            entityManager.persist(user);
            Board board = Board.builder().boardName("Comment locks").boardUrl("comment-locks")
                    .creator(user).build();
            entityManager.persist(board);
            Post post = Post.builder().title("Post").contents("Contents").user(user).board(board).build();
            entityManager.persist(post);
            Comment comment = Comment.builder().post(post).user(user).depth(0).content("Original").build();
            entityManager.persist(comment);
            userId = user.getUserId();
            boardId = board.getBoardId();
            postId = post.getPostId();
            commentId = comment.getCommentId();
        });
        postPort = mock(CommentPostPort.class);
        versionRepository = mock(CommentVersionRepository.class);
        rewardService = mock(ContentRewardService.class);
        commandService = new CommentCommandService(
                commentRepository, mock(CommentLikeRepository.class), versionRepository,
                mock(CommentClosureRepository.class), mock(CommentMentionRepository.class),
                mock(ActorWritePort.class), mock(CommentUserWritePort.class), postPort, rewardService,
                mock(CommentNotificationPort.class), mock(CommentStreamEventDispatcher.class),
                mock(SemanticSearchEventPublisher.class), mock(CommentLikeCommand.class),
                mock(BadgeEvaluationService.class), mock(AnonymousReadCacheInvalidator.class));
    }

    @AfterEach
    void cleanUp() {
        transactions.executeWithoutResult(status -> {
            entityManager.remove(entityManager.find(Comment.class, commentId));
            entityManager.remove(entityManager.find(Post.class, postId));
            entityManager.remove(entityManager.find(Board.class, boardId));
            entityManager.remove(entityManager.find(User.class, userId));
        });
    }

    @Test
    void deleteObservesDeletionCommittedAfterInitialLookup() throws Exception {
        Throwable failure = runAfterConcurrentChange(Comment::deleteComment,
                () -> commandService.deleteComment(userId, commentId));

        assertCommentNotFound(failure);
        verify(postPort, never()).decrementCommentCount(anyLong());
        verifyNoInteractions(versionRepository, rewardService);
        Boolean deleted = transactions.execute(status -> commentRepository.findById(commentId).orElseThrow()
                .getIsDeleted());
        assertThat(deleted).isTrue();
    }

    @Test
    void updateObservesBlindingCommittedAfterInitialLookup() throws Exception {
        Throwable failure = runAfterConcurrentChange(
                comment -> comment.blind("moderation", LocalDateTime.of(2026, 9, 9, 12, 0)),
                () -> commandService.updateComment(new CommentUpdateCommand(userId, commentId, "Rejected", null)));

        assertCommentNotFound(failure);
        verifyNoInteractions(versionRepository);
        Comment stored = transactions.execute(status -> commentRepository.findById(commentId).orElseThrow());
        assertThat(stored.getIsBlinded()).isTrue();
        assertThat(stored.getContent()).isEqualTo("Original");
    }

    @Test
    void updateUsesCommittedContentForHistoryAndPreservesConcurrentLikeCount() throws Exception {
        Throwable failure = runAfterConcurrentChange(comment -> {
            comment.updateContent("Concurrent edit");
            comment.incrementLikeCount();
        }, () -> commandService.updateComment(new CommentUpdateCommand(userId, commentId, "Final edit", null)));

        assertThat(failure).isNull();
        ArgumentCaptor<CommentVersion> version = ArgumentCaptor.forClass(CommentVersion.class);
        verify(versionRepository).save(version.capture());
        assertThat(version.getValue().getOriginalContent()).isEqualTo("Concurrent edit");
        Comment stored = transactions.execute(status -> commentRepository.findById(commentId).orElseThrow());
        assertThat(stored.getContent()).isEqualTo("Final edit");
        assertThat(stored.getLikeCount()).isEqualTo(1);
    }

    private Throwable runAfterConcurrentChange(Consumer<Comment> concurrentChange, Runnable command) throws Exception {
        CountDownLatch writerLocked = new CountDownLatch(1);
        CountDownLatch commandReachedParentLock = new CountDownLatch(1);
        when(postPort.lockForWrite(postId)).thenAnswer(invocation -> {
            // The command has completed its initial lookup in its own persistence context.
            commandReachedParentLock.countDown();
            entityManager.find(Board.class, boardId, LockModeType.PESSIMISTIC_WRITE);
            entityManager.find(Post.class, postId, LockModeType.PESSIMISTIC_WRITE);
            return new CommentPostSnapshot(postId, boardId, "comment-locks", "Comment locks",
                    null, "Post", userId, null, false, true, true);
        });

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> writer = executor.submit(() -> transactions.executeWithoutResult(status -> {
                entityManager.find(Board.class, boardId, LockModeType.PESSIMISTIC_WRITE);
                entityManager.find(Post.class, postId, LockModeType.PESSIMISTIC_WRITE);
                Comment comment = commentRepository.findByIdWithRelationsForUpdate(commentId).orElseThrow();
                writerLocked.countDown();
                await(commandReachedParentLock);
                concurrentChange.accept(comment);
                entityManager.flush();
            }));
            Throwable failure = null;
            try {
                await(writerLocked);
                transactions.executeWithoutResult(status -> command.run());
            } catch (Throwable error) {
                failure = error;
            } finally {
                commandReachedParentLock.countDown();
            }
            writer.get(15, TimeUnit.SECONDS);
            return failure;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).as("transaction rendezvous").isTrue();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private static void assertCommentNotFound(Throwable failure) {
        assertThat(failure).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COMMENT_NOT_FOUND));
    }
}
