package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class SemanticSearchIndexService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final EmbeddingClient embeddingClient;
    private final SemanticSearchTextBuilder textBuilder;
    private final SemanticSearchEmbeddingRepository embeddingRepository;

    @Transactional
    public void upsertPost(Long postId) {
        Post post = postRepository.findByIdWithRelations(postId).orElse(null);
        if (post == null || Boolean.TRUE.equals(post.getIsDeleted())) {
            embeddingRepository.tombstone("POST", postId);
            embeddingRepository.tombstoneCommentsByPostId(postId);
            return;
        }
        String embeddingText = textBuilder.buildPostText(post);
        float[] embedding = embeddingClient.embed(embeddingText);
        embeddingRepository.upsertPost(
                post.getPostId(),
                post.getBoard().getBoardId(),
                post.getUser().getUserId(),
                post.getAgent() != null ? post.getAgent().getAgentId() : null,
                embeddingText,
                textBuilder.hash(embeddingText),
                embeddingClient.model(),
                embedding);
    }

    @Transactional
    public void upsertComment(Long commentId) {
        Comment comment = commentRepository.findByIdWithRelations(commentId).orElse(null);
        if (comment == null
                || Boolean.TRUE.equals(comment.getIsDeleted())
                || comment.getPost() == null
                || Boolean.TRUE.equals(comment.getPost().getIsDeleted())) {
            embeddingRepository.tombstone("COMMENT", commentId);
            return;
        }
        String embeddingText = textBuilder.buildCommentText(comment);
        float[] embedding = embeddingClient.embed(embeddingText);
        embeddingRepository.upsertComment(
                comment.getCommentId(),
                comment.getPost().getPostId(),
                comment.getPost().getBoard().getBoardId(),
                comment.getUser().getUserId(),
                comment.getAgent() != null ? comment.getAgent().getAgentId() : null,
                embeddingText,
                textBuilder.hash(embeddingText),
                embeddingClient.model(),
                embedding);
    }

    @Transactional
    public void delete(String contentType, Long contentId) {
        embeddingRepository.tombstone(contentType, contentId);
        if ("POST".equals(contentType)) {
            embeddingRepository.tombstoneCommentsByPostId(contentId);
        }
    }
}
