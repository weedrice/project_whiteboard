package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SemanticSearchIndexTransactionServiceTest {

    private PostRepository postRepository;
    private CommentRepository commentRepository;
    private BoardRepository boardRepository;
    private SemanticSearchEmbeddingRepository embeddingRepository;
    private SemanticSearchTextBuilder textBuilder;
    private SemanticSearchIndexTransactionService transactionService;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        commentRepository = mock(CommentRepository.class);
        boardRepository = mock(BoardRepository.class);
        embeddingRepository = mock(SemanticSearchEmbeddingRepository.class);
        textBuilder = new SemanticSearchTextBuilder();
        transactionService = new SemanticSearchIndexTransactionService(
                postRepository,
                commentRepository,
                boardRepository,
                textBuilder,
                embeddingRepository);
    }

    @Test
    void transactionAnnotationsDeclareShortReadAndWriteBoundaries() throws NoSuchMethodException {
        assertThat(transactionalAnnotation("loadPostIndexPayload", Long.class).readOnly()).isTrue();
        assertThat(transactionalAnnotation("loadCommentIndexPayload", Long.class).readOnly()).isTrue();
        assertThat(transactionalAnnotation("upsertPost", SemanticSearchPostIndexPayload.class, String.class, float[].class)
                .readOnly()).isFalse();
        assertThat(transactionalAnnotation("upsertComment", SemanticSearchCommentIndexPayload.class, String.class, float[].class)
                .readOnly()).isFalse();
        assertThat(transactionalAnnotation("tombstonePost", Long.class).readOnly()).isFalse();
        assertThat(transactionalAnnotation("tombstoneComment", Long.class).readOnly()).isFalse();
    }

    @Test
    void upsertPostRejectsStalePayloadWhenCurrentContentChanged() {
        Post currentPost = post("new title", "new body");
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(currentPost));
        SemanticSearchPostIndexPayload stalePayload =
                new SemanticSearchPostIndexPayload(1L, 10L, 100L, null, "old text", "old-hash");

        assertThatThrownBy(() -> transactionService.upsertPost(stalePayload, "model", new float[] { 0.1F }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("post payload changed");

        verify(embeddingRepository, never()).upsertPost(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void upsertCommentRejectsStalePayloadWhenCurrentContentChanged() {
        Post post = post("title", "body");
        Comment currentComment = Comment.builder()
                .post(post)
                .user(post.getUser())
                .content("new comment")
                .build();
        ReflectionTestUtils.setField(currentComment, "commentId", 2L);
        when(commentRepository.findByIdWithRelationsForUpdate(2L)).thenReturn(Optional.of(currentComment));
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        SemanticSearchCommentIndexPayload stalePayload =
                new SemanticSearchCommentIndexPayload(2L, 1L, 10L, 100L, null, "old text", "old-hash");

        assertThatThrownBy(() -> transactionService.upsertComment(stalePayload, "model", new float[] { 0.1F }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("comment payload changed");

        verify(embeddingRepository, never()).upsertComment(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void upsertPostLocksSourceRowsBeforeWritingEmbedding() {
        Post post = post("title", "body");
        String embeddingText = textBuilder.buildPostText(post);
        SemanticSearchPostIndexPayload payload =
                new SemanticSearchPostIndexPayload(1L, 10L, 100L, null, embeddingText, textBuilder.hash(embeddingText));
        float[] embedding = new float[] { 0.1F };
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));

        transactionService.upsertPost(payload, "model", embedding);

        var inOrder = inOrder(postRepository, boardRepository, embeddingRepository);
        inOrder.verify(postRepository).findByIdWithRelationsForUpdate(1L);
        inOrder.verify(boardRepository).findByIdForUpdate(10L);
        inOrder.verify(embeddingRepository).upsertPost(
                1L, 10L, 100L, null, embeddingText, textBuilder.hash(embeddingText), "model", embedding);
    }

    @Test
    void upsertCommentLocksSourceRowsBeforeWritingEmbedding() {
        Post post = post("title", "body");
        Comment comment = Comment.builder()
                .post(post)
                .user(post.getUser())
                .content("comment")
                .build();
        ReflectionTestUtils.setField(comment, "commentId", 2L);
        String embeddingText = textBuilder.buildCommentText(comment);
        SemanticSearchCommentIndexPayload payload =
                new SemanticSearchCommentIndexPayload(
                        2L, 1L, 10L, 100L, null, embeddingText, textBuilder.hash(embeddingText));
        float[] embedding = new float[] { 0.1F };
        when(commentRepository.findByIdWithRelationsForUpdate(2L)).thenReturn(Optional.of(comment));
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));

        transactionService.upsertComment(payload, "model", embedding);

        var inOrder = inOrder(commentRepository, postRepository, boardRepository, embeddingRepository);
        inOrder.verify(commentRepository).findByIdWithRelationsForUpdate(2L);
        inOrder.verify(postRepository).findByIdWithRelationsForUpdate(1L);
        inOrder.verify(boardRepository).findByIdForUpdate(10L);
        inOrder.verify(embeddingRepository).upsertComment(
                2L, 1L, 10L, 100L, null, embeddingText, textBuilder.hash(embeddingText), "model", embedding);
    }

    private Transactional transactionalAnnotation(String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = SemanticSearchIndexTransactionService.class.getMethod(methodName, parameterTypes);
        return method.getAnnotation(Transactional.class);
    }

    private Post post(String title, String contents) {
        User user = User.builder().displayName("writer").build();
        ReflectionTestUtils.setField(user, "userId", 100L);
        Board board = Board.builder().boardName("board").boardUrl("board").creator(user).build();
        ReflectionTestUtils.setField(board, "boardId", 10L);
        Post post = Post.builder()
                .board(board)
                .user(user)
                .title(title)
                .contents(contents)
                .build();
        ReflectionTestUtils.setField(post, "postId", 1L);
        return post;
    }
}
