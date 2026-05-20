package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
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
    private final SemanticSearchTextBuilder textBuilder;
    private final SemanticSearchEmbeddingRepository embeddingRepository;

    @Transactional(readOnly = true)
    public SemanticSearchPostIndexPayload loadPostIndexPayload(Long postId) {
        Post post = postRepository.findByIdWithRelations(postId).orElse(null);
        if (post == null || Boolean.TRUE.equals(post.getIsDeleted())) {
            return null;
        }
        String embeddingText = textBuilder.buildPostText(post);
        return new SemanticSearchPostIndexPayload(
                post.getPostId(),
                post.getBoard().getBoardId(),
                post.getUser().getUserId(),
                post.getAgent() != null ? post.getAgent().getAgentId() : null,
                embeddingText,
                textBuilder.hash(embeddingText));
    }

    @Transactional(readOnly = true)
    public SemanticSearchCommentIndexPayload loadCommentIndexPayload(Long commentId) {
        Comment comment = commentRepository.findByIdWithRelations(commentId).orElse(null);
        if (isInactiveComment(comment)) {
            return null;
        }
        String embeddingText = textBuilder.buildCommentText(comment);
        return new SemanticSearchCommentIndexPayload(
                comment.getCommentId(),
                comment.getPost().getPostId(),
                comment.getPost().getBoard().getBoardId(),
                comment.getUser().getUserId(),
                comment.getAgent() != null ? comment.getAgent().getAgentId() : null,
                embeddingText,
                textBuilder.hash(embeddingText));
    }

    @Transactional
    public void upsertPost(SemanticSearchPostIndexPayload payload, String embeddingModel, float[] embedding) {
        Post post = postRepository.findByIdWithRelationsForUpdate(payload.postId()).orElse(null);
        if (post == null || Boolean.TRUE.equals(post.getIsDeleted())) {
            tombstonePost(payload.postId());
            return;
        }
        boardRepository.findByIdForUpdate(post.getBoard().getBoardId());
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
        Comment comment = commentRepository.findByIdWithRelationsForUpdate(payload.commentId()).orElse(null);
        if (isInactiveComment(comment)) {
            tombstoneComment(payload.commentId());
            return;
        }
        Post post = postRepository.findByIdWithRelationsForUpdate(comment.getPost().getPostId()).orElse(null);
        if (post == null || Boolean.TRUE.equals(post.getIsDeleted())) {
            tombstoneComment(payload.commentId());
            return;
        }
        boardRepository.findByIdForUpdate(post.getBoard().getBoardId());
        validateCurrentCommentPayload(payload, comment);
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
        embeddingRepository.tombstone("POST", postId);
        embeddingRepository.tombstoneCommentsByPostId(postId);
    }

    @Transactional
    public void tombstoneComment(Long commentId) {
        embeddingRepository.tombstone("COMMENT", commentId);
    }

    @Transactional
    public void delete(String contentType, Long contentId) {
        embeddingRepository.tombstone(contentType, contentId);
        if ("POST".equals(contentType)) {
            embeddingRepository.tombstoneCommentsByPostId(contentId);
        }
    }

    private boolean isInactiveComment(Comment comment) {
        return comment == null
                || Boolean.TRUE.equals(comment.getIsDeleted())
                || comment.getPost() == null
                || Boolean.TRUE.equals(comment.getPost().getIsDeleted());
    }

    private void validateCurrentPostPayload(SemanticSearchPostIndexPayload payload, Post post) {
        String currentEmbeddingText = textBuilder.buildPostText(post);
        if (!Objects.equals(payload.boardId(), post.getBoard().getBoardId())
                || !Objects.equals(payload.authorUserId(), post.getUser().getUserId())
                || !Objects.equals(payload.authorAgentId(), post.getAgent() != null ? post.getAgent().getAgentId() : null)
                || !Objects.equals(payload.embeddingHash(), textBuilder.hash(currentEmbeddingText))) {
            throw new IllegalStateException("Semantic search post payload changed before write");
        }
    }

    private void validateCurrentCommentPayload(SemanticSearchCommentIndexPayload payload, Comment comment) {
        String currentEmbeddingText = textBuilder.buildCommentText(comment);
        if (!Objects.equals(payload.postId(), comment.getPost().getPostId())
                || !Objects.equals(payload.boardId(), comment.getPost().getBoard().getBoardId())
                || !Objects.equals(payload.authorUserId(), comment.getUser().getUserId())
                || !Objects.equals(payload.authorAgentId(), comment.getAgent() != null ? comment.getAgent().getAgentId() : null)
                || !Objects.equals(payload.embeddingHash(), textBuilder.hash(currentEmbeddingText))) {
            throw new IllegalStateException("Semantic search comment payload changed before write");
        }
    }
}
