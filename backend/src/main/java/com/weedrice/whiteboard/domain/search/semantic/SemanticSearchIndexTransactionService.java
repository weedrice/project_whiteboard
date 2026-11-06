package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
class SemanticSearchIndexTransactionService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final SemanticSearchTextBuilder textBuilder;
    private final SemanticSearchEmbeddingRepository embeddingRepository;

    @Transactional(readOnly = true)
    public SemanticSearchPostIndexPayload loadPostIndexPayload(Long postId) {
        Post post = postRepository.findByIdWithRelations(postId).orElse(null);
        if (!isIndexablePost(post)) {
            return null;
        }
        String embeddingText = textBuilder.buildPostText(post);
        return new SemanticSearchPostIndexPayload(
                post.getPostId(),
                post.getBoard().getBoardId(),
                post.getUserId(),
                post.getAgentId(),
                embeddingText,
                textBuilder.hash(embeddingText));
    }

    @Transactional(readOnly = true)
    public SemanticSearchCommentIndexPayload loadCommentIndexPayload(Long commentId) {
        Comment comment = commentRepository.findByIdWithRelations(commentId).orElse(null);
        Post post = comment == null ? null : postRepository.findByIdWithRelations(comment.getPostId()).orElse(null);
        if (!isIndexableComment(comment, post)) {
            return null;
        }
        String embeddingText = textBuilder.buildCommentText(comment, post);
        return new SemanticSearchCommentIndexPayload(
                comment.getCommentId(),
                comment.getPostId(),
                post.getBoard().getBoardId(),
                comment.getUserId(),
                comment.getAgentId(),
                embeddingText,
                textBuilder.hash(embeddingText));
    }

    @Transactional
    public boolean reusePostEmbedding(SemanticSearchPostIndexPayload payload, String model) {
        boardRepository.findByIdForUpdate(payload.boardId());
        Post post = postRepository.findByIdWithRelationsForUpdate(payload.postId()).orElse(null);
        if (!isIndexablePost(post)) {
            tombstonePost(payload.postId());
            return true;
        }
        validateCurrentPostPayload(payload, post);
        return embeddingRepository.reuse("POST", payload.postId(), payload.postId(), payload.boardId(),
                payload.authorUserId(), payload.authorAgentId(), payload.embeddingText(), payload.embeddingHash(), model);
    }

    @Transactional
    public boolean reuseCommentEmbedding(SemanticSearchCommentIndexPayload payload, String model) {
        boardRepository.findByIdForUpdate(payload.boardId());
        Post post = postRepository.findByIdWithRelationsForUpdate(payload.postId()).orElse(null);
        Comment comment = commentRepository.findByIdWithRelationsForUpdate(payload.commentId()).orElse(null);
        if (!isIndexableComment(comment, post)) {
            tombstoneComment(payload.commentId());
            return true;
        }
        validateCurrentCommentPayload(payload, comment, post);
        return embeddingRepository.reuse("COMMENT", payload.commentId(), payload.postId(), payload.boardId(),
                payload.authorUserId(), payload.authorAgentId(), payload.embeddingText(), payload.embeddingHash(), model);
    }

    @Transactional
    public void upsertPost(SemanticSearchPostIndexPayload payload, String embeddingModel, float[] embedding) {
        boardRepository.findByIdForUpdate(payload.boardId());
        Post post = postRepository.findByIdWithRelationsForUpdate(payload.postId()).orElse(null);
        if (!isIndexablePost(post)) {
            tombstonePost(payload.postId());
            return;
        }
        validateCurrentPostPayload(payload, post);
        embeddingRepository.upsertPost(
                payload.postId(),
                payload.boardId(),
                payload.authorUserId(),
                payload.authorAgentId(),
                payload.embeddingText(),
                payload.embeddingHash(),
                embeddingModel,
                embedding);
    }

    @Transactional
    public void upsertComment(SemanticSearchCommentIndexPayload payload, String embeddingModel, float[] embedding) {
        boardRepository.findByIdForUpdate(payload.boardId());
        Post post = postRepository.findByIdWithRelationsForUpdate(payload.postId()).orElse(null);
        if (!isIndexablePost(post)) {
            tombstoneComment(payload.commentId());
            return;
        }
        Comment comment = commentRepository.findByIdWithRelationsForUpdate(payload.commentId()).orElse(null);
        if (!isIndexableComment(comment, post)) {
            tombstoneComment(payload.commentId());
            return;
        }
        validateCurrentCommentPayload(payload, comment, post);
        embeddingRepository.upsertComment(
                payload.commentId(),
                payload.postId(),
                payload.boardId(),
                payload.authorUserId(),
                payload.authorAgentId(),
                payload.embeddingText(),
                payload.embeddingHash(),
                embeddingModel,
                embedding);
    }

    @Transactional
    public void tombstonePost(Long postId) {
        Post current = lockPostForCleanup(postId);
        if (!isIndexablePost(current)) {
            embeddingRepository.tombstone("POST", postId);
            embeddingRepository.tombstoneCommentsByPostId(postId);
        }
    }

    @Transactional
    public void tombstoneComment(Long commentId) {
        Long postId = commentRepository.findPostIdByCommentId(commentId).orElse(null);
        Post currentPost = postId == null ? null : lockPostForCleanup(postId);
        Comment current = commentRepository.findByIdWithRelationsForUpdate(commentId).orElse(null);
        if (current != null && !Objects.equals(postId, current.getPostId())) {
            throw new IllegalStateException("Semantic search comment relationship changed before cleanup");
        }
        if (!isIndexableComment(current, currentPost)) {
            embeddingRepository.tombstone("COMMENT", commentId);
        }
    }

    @Transactional
    public void delete(String contentType, Long contentId) {
        if ("POST".equals(contentType)) {
            tombstonePost(contentId);
        } else if ("COMMENT".equals(contentType)) {
            tombstoneComment(contentId);
        } else {
            throw new IllegalArgumentException("Unsupported semantic search content type");
        }
    }

    private Post lockPostForCleanup(Long postId) {
        // Scalar reads avoid caching an unlocked entity before the locking read.
        Long boardId = postRepository.findBoardIdByPostId(postId).orElse(null);
        if (boardId != null) {
            boardRepository.findByIdForUpdate(boardId);
        }
        Post post = postRepository.findByIdWithRelationsForUpdate(postId).orElse(null);
        if (post != null && !Objects.equals(boardId, post.getBoard().getBoardId())) {
            throw new IllegalStateException("Semantic search post board changed before cleanup");
        }
        return post;
    }

    private boolean isIndexableComment(Comment comment, Post post) {
        return comment != null
                && !Boolean.TRUE.equals(comment.getIsDeleted())
                && !Boolean.TRUE.equals(comment.getIsBlinded())
                && isIndexableUser(comment.getUserId())
                && isIndexablePost(post);
    }

    private boolean isIndexablePost(Post post) {
        return post != null
                && !Boolean.TRUE.equals(post.getIsDeleted())
                && !Boolean.TRUE.equals(post.getIsBlinded())
                && !Boolean.TRUE.equals(post.getIsSecret())
                && isIndexableBoard(post.getBoard())
                && isIndexableUser(post.getUserId());
    }

    private boolean isIndexableBoard(com.weedrice.whiteboard.domain.board.entity.Board board) {
        return board != null
                && Boolean.TRUE.equals(board.getIsActive())
                && Boolean.TRUE.equals(board.getIsPublic());
    }

    private boolean isIndexableUser(Long userId) {
        return userId != null && userRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, User.STATUS_ACTIVE)
                .isPresent();
    }

    private void validateCurrentPostPayload(SemanticSearchPostIndexPayload payload, Post post) {
        String currentEmbeddingText = textBuilder.buildPostText(post);
        if (!Objects.equals(payload.boardId(), post.getBoard().getBoardId())
                || !Objects.equals(payload.authorUserId(), post.getUserId())
                || !Objects.equals(payload.authorAgentId(), post.getAgentId())
                || !Objects.equals(payload.embeddingHash(), textBuilder.hash(currentEmbeddingText))) {
            throw new IllegalStateException("Semantic search post payload changed before write");
        }
    }

    private void validateCurrentCommentPayload(SemanticSearchCommentIndexPayload payload, Comment comment, Post post) {
        String currentEmbeddingText = textBuilder.buildCommentText(comment, post);
        if (!Objects.equals(payload.postId(), comment.getPostId())
                || !Objects.equals(payload.boardId(), post.getBoard().getBoardId())
                || !Objects.equals(payload.authorUserId(), comment.getUserId())
                || !Objects.equals(payload.authorAgentId(), comment.getAgentId())
                || !Objects.equals(payload.embeddingHash(), textBuilder.hash(currentEmbeddingText))) {
            throw new IllegalStateException("Semantic search comment payload changed before write");
        }
    }
}
