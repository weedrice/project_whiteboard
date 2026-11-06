package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
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
    private UserRepository userRepository;
    private SemanticSearchEmbeddingRepository embeddingRepository;
    private SemanticSearchTextBuilder textBuilder;
    private SemanticSearchIndexTransactionService transactionService;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        commentRepository = mock(CommentRepository.class);
        boardRepository = mock(BoardRepository.class);
        userRepository = mock(UserRepository.class);
        embeddingRepository = mock(SemanticSearchEmbeddingRepository.class);
        textBuilder = new SemanticSearchTextBuilder();
        transactionService = new SemanticSearchIndexTransactionService(
                postRepository,
                commentRepository,
                boardRepository,
                userRepository,
                textBuilder,
                embeddingRepository);
    }

    @Test
    void reusePostLocksAndRevalidatesBeforeUpdatingVectorMetadata() {
        Post current = post("title", "body");
        String text = textBuilder.buildPostText(current);
        var payload = new SemanticSearchPostIndexPayload(1L, 10L, 100L, null, text, textBuilder.hash(text));
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(current));
        when(embeddingRepository.reuse("POST", 1L, 1L, 10L, 100L, null, text, textBuilder.hash(text), "model"))
                .thenReturn(true);

        assertThat(transactionService.reusePostEmbedding(payload, "model")).isTrue();

        var order = inOrder(boardRepository, postRepository, embeddingRepository);
        order.verify(boardRepository).findByIdForUpdate(10L);
        order.verify(postRepository).findByIdWithRelationsForUpdate(1L);
        order.verify(embeddingRepository).reuse("POST", 1L, 1L, 10L, 100L, null, text, textBuilder.hash(text), "model");
    }

    @Test
    void reuseCommentRejectsChangedParentTitle() {
        Post current = post("changed title", "body");
        Comment comment = comment(current, "comment");
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(current));
        when(commentRepository.findByIdWithRelationsForUpdate(2L)).thenReturn(Optional.of(comment));
        var payload = new SemanticSearchCommentIndexPayload(2L, 1L, 10L, 100L, null, "old text", "old hash");

        assertThatThrownBy(() -> transactionService.reuseCommentEmbedding(payload, "model"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("payload changed");
        verifyNoInteractions(embeddingRepository);
    }

    @Test
    void reusePostRejectsChangedContentBeforeReactivatingTombstone() {
        Post changed = post("new title", "body");
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(changed));
        var payload = new SemanticSearchPostIndexPayload(1L, 10L, 100L, null, "old text", "old hash");

        assertThatThrownBy(() -> transactionService.reusePostEmbedding(payload, "model"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("payload changed");
        verifyNoInteractions(embeddingRepository);
    }

    @Test
    void reusePostTombstonesIfSourceBecameSecret() {
        Post current = post("title", "body");
        ReflectionTestUtils.setField(current, "isSecret", true);
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(current));
        var payload = new SemanticSearchPostIndexPayload(1L, 10L, 100L, null, "text", "hash");

        assertThat(transactionService.reusePostEmbedding(payload, "model")).isTrue();

        verify(embeddingRepository).tombstone("POST", 1L);
        verify(embeddingRepository).tombstoneCommentsByPostId(1L);
        verify(embeddingRepository, never()).reuse(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reuseCommentPreservesMetadataAndReturnsMissForDifferentStoredModel() {
        Post current = post("title", "body");
        Comment comment = comment(current, "comment");
        String text = textBuilder.buildCommentText(comment, current);
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(current));
        when(commentRepository.findByIdWithRelationsForUpdate(2L)).thenReturn(Optional.of(comment));
        var payload = new SemanticSearchCommentIndexPayload(2L, 1L, 10L, 100L, null, text, textBuilder.hash(text));

        assertThat(transactionService.reuseCommentEmbedding(payload, "new-model")).isFalse();
        verify(embeddingRepository).reuse("COMMENT", 2L, 1L, 10L, 100L, null, text, textBuilder.hash(text), "new-model");
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
                .userId(post.getUserId())
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
    void upsertCommentRejectsPayloadWhenCommentRelationshipChangedAfterEmbedding() {
        Post indexedPost = post("title", "body");
        Comment indexedComment = comment(indexedPost, "comment");
        String embeddingText = textBuilder.buildCommentText(indexedComment, indexedPost);
        SemanticSearchCommentIndexPayload payload = new SemanticSearchCommentIndexPayload(
                2L, 1L, 10L, 100L, null, embeddingText, textBuilder.hash(embeddingText));

        Post movedPost = post("moved title", "moved body");
        ReflectionTestUtils.setField(movedPost, "postId", 3L);
        ReflectionTestUtils.setField(movedPost.getBoard(), "boardId", 30L);
        Comment movedComment = comment(movedPost, "comment");
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(indexedPost));
        when(commentRepository.findByIdWithRelationsForUpdate(2L)).thenReturn(Optional.of(movedComment));

        assertThatThrownBy(() -> transactionService.upsertComment(payload, "model", new float[] { 0.1F }))
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

        var inOrder = inOrder(boardRepository, postRepository, embeddingRepository);
        inOrder.verify(boardRepository).findByIdForUpdate(10L);
        inOrder.verify(postRepository).findByIdWithRelationsForUpdate(1L);
        inOrder.verify(embeddingRepository).upsertPost(
                1L, 10L, 100L, null, embeddingText, textBuilder.hash(embeddingText), "model", embedding);
    }

    @Test
    void upsertCommentLocksSourceRowsBeforeWritingEmbedding() {
        Post post = post("title", "body");
        Comment comment = Comment.builder()
                .post(post)
                .userId(post.getUserId())
                .content("comment")
                .build();
        ReflectionTestUtils.setField(comment, "commentId", 2L);
        String embeddingText = textBuilder.buildCommentText(comment, post);
        SemanticSearchCommentIndexPayload payload =
                new SemanticSearchCommentIndexPayload(
                        2L, 1L, 10L, 100L, null, embeddingText, textBuilder.hash(embeddingText));
        float[] embedding = new float[] { 0.1F };
        when(commentRepository.findByIdWithRelationsForUpdate(2L)).thenReturn(Optional.of(comment));
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));

        transactionService.upsertComment(payload, "model", embedding);

        var inOrder = inOrder(boardRepository, postRepository, commentRepository, embeddingRepository);
        inOrder.verify(boardRepository).findByIdForUpdate(10L);
        inOrder.verify(postRepository).findByIdWithRelationsForUpdate(1L);
        inOrder.verify(commentRepository).findByIdWithRelationsForUpdate(2L);
        inOrder.verify(embeddingRepository).upsertComment(
                2L, 1L, 10L, 100L, null, embeddingText, textBuilder.hash(embeddingText), "model", embedding);
    }

    @Test
    void loadPostIndexPayloadSkipsSecretPostBeforeEmbedding() {
        Post post = post("title", "secret body");
        ReflectionTestUtils.setField(post, "isSecret", true);
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));

        assertThat(transactionService.loadPostIndexPayload(1L)).isNull();
    }

    @Test
    void loadPostIndexPayloadSkipsPrivateBoardBeforeEmbedding() {
        Post post = post("title", "private board body");
        ReflectionTestUtils.setField(post.getBoard(), "isPublic", false);
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));

        assertThat(transactionService.loadPostIndexPayload(1L)).isNull();
    }

    @Test
    void loadPostIndexPayloadSkipsInactiveBoardBeforeEmbedding() {
        Post post = post("title", "inactive board body");
        ReflectionTestUtils.setField(post.getBoard(), "isActive", false);
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));

        assertThat(transactionService.loadPostIndexPayload(1L)).isNull();
    }

    @Test
    void loadPostIndexPayloadSkipsInactiveAuthorBeforeEmbedding() {
        Post post = post("title", "inactive author body");
        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(100L, User.STATUS_ACTIVE))
                .thenReturn(Optional.empty());
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));

        assertThat(transactionService.loadPostIndexPayload(1L)).isNull();
    }

    @Test
    void upsertPostTombstonesWhenPostBecameUnindexableAfterEmbedding() {
        Post post = post("title", "body");
        String embeddingText = textBuilder.buildPostText(post);
        SemanticSearchPostIndexPayload payload =
                new SemanticSearchPostIndexPayload(1L, 10L, 100L, null, embeddingText, textBuilder.hash(embeddingText));
        ReflectionTestUtils.setField(post, "isSecret", true);
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));

        transactionService.upsertPost(payload, "model", new float[] { 0.1F });

        verify(embeddingRepository).tombstone("POST", 1L);
        verify(embeddingRepository).tombstoneCommentsByPostId(1L);
        verify(embeddingRepository, never()).upsertPost(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void loadCommentIndexPayloadSkipsCommentOnSecretPostBeforeEmbedding() {
        Post post = post("title", "body");
        ReflectionTestUtils.setField(post, "isSecret", true);
        Comment comment = comment(post, "hidden comment");
        when(commentRepository.findByIdWithRelations(2L)).thenReturn(Optional.of(comment));

        assertThat(transactionService.loadCommentIndexPayload(2L)).isNull();
    }

    @Test
    void loadCommentIndexPayloadSkipsCommentOnPrivateBoardBeforeEmbedding() {
        Post post = post("title", "body");
        ReflectionTestUtils.setField(post.getBoard(), "isPublic", false);
        Comment comment = comment(post, "hidden comment");
        when(commentRepository.findByIdWithRelations(2L)).thenReturn(Optional.of(comment));

        assertThat(transactionService.loadCommentIndexPayload(2L)).isNull();
    }

    @Test
    void loadCommentIndexPayloadSkipsInactiveCommentAuthorBeforeEmbedding() {
        Post post = post("title", "body");
        Comment comment = comment(post, "hidden comment");
        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(100L, User.STATUS_ACTIVE))
                .thenReturn(Optional.empty());
        when(commentRepository.findByIdWithRelations(2L)).thenReturn(Optional.of(comment));

        assertThat(transactionService.loadCommentIndexPayload(2L)).isNull();
    }

    @Test
    void upsertCommentTombstonesWhenParentPostBecameUnindexableAfterEmbedding() {
        Post post = post("title", "body");
        Comment comment = comment(post, "comment");
        String embeddingText = textBuilder.buildCommentText(comment, post);
        SemanticSearchCommentIndexPayload payload =
                new SemanticSearchCommentIndexPayload(
                        2L, 1L, 10L, 100L, null, embeddingText, textBuilder.hash(embeddingText));
        ReflectionTestUtils.setField(post.getBoard(), "isActive", false);
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));

        transactionService.upsertComment(payload, "model", new float[] { 0.1F });

        verify(embeddingRepository).tombstone("COMMENT", 2L);
        verify(embeddingRepository, never()).upsertComment(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void lateDeletePreservesPostAndCommentVectorsAfterPostRestored() {
        Post restored = post("restored", "body");
        when(postRepository.findBoardIdByPostId(1L)).thenReturn(Optional.of(10L));
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(restored));

        transactionService.delete("POST", 1L);

        verifyNoInteractions(embeddingRepository);
        var order = inOrder(boardRepository, postRepository);
        order.verify(postRepository).findBoardIdByPostId(1L);
        order.verify(boardRepository).findByIdForUpdate(10L);
        order.verify(postRepository).findByIdWithRelationsForUpdate(1L);
    }

    @Test
    void lateDeletePreservesCommentVectorAfterCommentRestored() {
        Post post = post("title", "body");
        Comment restored = comment(post, "restored");
        when(commentRepository.findPostIdByCommentId(2L)).thenReturn(Optional.of(1L));
        when(postRepository.findBoardIdByPostId(1L)).thenReturn(Optional.of(10L));
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(commentRepository.findByIdWithRelationsForUpdate(2L)).thenReturn(Optional.of(restored));

        transactionService.delete("COMMENT", 2L);

        verifyNoInteractions(embeddingRepository);
    }

    @Test
    void deleteTombstonesPostAndChildrenWhenStillHidden() {
        Post hidden = post("hidden", "body");
        ReflectionTestUtils.setField(hidden, "isBlinded", true);
        when(postRepository.findBoardIdByPostId(1L)).thenReturn(Optional.of(10L));
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(hidden));

        transactionService.delete("POST", 1L);

        verify(embeddingRepository).tombstone("POST", 1L);
        verify(embeddingRepository).tombstoneCommentsByPostId(1L);
    }

    @Test
    void cleanupRetriesIfPostMovedBetweenScalarLookupAndLock() {
        Post moved = post("moved", "body");
        ReflectionTestUtils.setField(moved.getBoard(), "boardId", 20L);
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(moved));

        assertThatThrownBy(() -> transactionService.delete("POST", 1L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("board changed");

        verifyNoInteractions(embeddingRepository);
    }

    @Test
    void cleanupRetriesIfCommentMovedBetweenScalarLookupAndLock() {
        Post original = post("original", "body");
        Comment moved = comment(original, "moved");
        ReflectionTestUtils.setField(moved, "postId", 3L);
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(original));
        when(commentRepository.findByIdWithRelationsForUpdate(2L)).thenReturn(Optional.of(moved));

        assertThatThrownBy(() -> transactionService.delete("COMMENT", 2L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("relationship changed");

        verifyNoInteractions(embeddingRepository);
    }

    private Transactional transactionalAnnotation(String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = SemanticSearchIndexTransactionService.class.getMethod(methodName, parameterTypes);
        return method.getAnnotation(Transactional.class);
    }

    private Post post(String title, String contents) {
        User user = User.builder().displayName("writer").build();
        ReflectionTestUtils.setField(user, "userId", 100L);
        lenient().when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(100L, User.STATUS_ACTIVE))
                .thenReturn(Optional.of(user));
        Board board = Board.builder().boardName("board").boardUrl("board").creator(user).build();
        ReflectionTestUtils.setField(board, "boardId", 10L);
        Post post = Post.builder()
                .board(board)
                .user(user)
                .title(title)
                .contents(contents)
                .build();
        ReflectionTestUtils.setField(post, "postId", 1L);
        lenient().when(postRepository.findBoardIdByPostId(1L)).thenReturn(Optional.of(10L));
        return post;
    }

    private Comment comment(Post post, String content) {
        Comment comment = Comment.builder()
                .post(post)
                .userId(post.getUserId())
                .content(content)
                .build();
        ReflectionTestUtils.setField(comment, "commentId", 2L);
        lenient().when(commentRepository.findPostIdByCommentId(2L)).thenReturn(Optional.of(post.getPostId()));
        return comment;
    }
}
