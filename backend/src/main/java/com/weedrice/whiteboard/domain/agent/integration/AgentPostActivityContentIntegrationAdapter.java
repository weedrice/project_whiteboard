package com.weedrice.whiteboard.domain.agent.integration;

import com.weedrice.whiteboard.domain.agent.port.AgentPostActivityContentPort;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.BlockedUserIdsParameter;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AgentPostActivityContentIntegrationAdapter implements AgentPostActivityContentPort {
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final BoardRepository boardRepository;
    private final UserBlockService userBlockService;
    private final EntityManager entityManager;

    @Override
    public void validateOwnedActivePost(Long agentId, Long postId) {
        validateOwned(agentId, loadPost(postId));
    }

    @Override
    public LocalDateTime resolveObservedBoundary(Long agentId, Long postId, List<Long> observedCommentIds) {
        if (observedCommentIds == null || observedCommentIds.isEmpty() || observedCommentIds.size() > 1000
                || observedCommentIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        // Match comment creation's board -> post lock order. All root/reply and agent creation
        // paths acquire this same post lock before assigning their comment creation time.
        Long boardId = postRepository.findBoardIdByPostId(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        Board board = boardRepository.findByIdForUpdate(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        entityManager.refresh(board);
        Post post = postRepository.findByIdWithRelationsForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        entityManager.refresh(post);
        if (!Objects.equals(boardId, post.getBoard().getBoardId())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        validateOwned(agentId, post);
        BlockedUserIdsParameter blocked = blocked(post);
        Set<Long> ids = new HashSet<>(observedCommentIds);
        List<Comment> observed = commentRepository.findAllById(ids);
        if (observed.size() != ids.size() || observed.stream().anyMatch(comment ->
                !Objects.equals(comment.getPostId(), postId) || Boolean.TRUE.equals(comment.getIsDeleted())
                || Boolean.TRUE.equals(comment.getIsBlinded()) || blocked.ids().contains(comment.getUserId())
                || comment.getCreatedAt() == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        LocalDateTime through = observed.stream().map(Comment::getCreatedAt).max(LocalDateTime::compareTo).orElseThrow();
        // The DB cursor has microsecond precision and cannot encode a partial timestamp group.
        // Keep the entire first unobserved group unread, even if some peers were observed.
        return commentRepository.findFirstUnobservedActivityAt(agentId, postId, blocked.empty(), blocked.ids(), through, ids)
                .map(firstUnobserved -> firstUnobserved.minusNanos(1000))
                .orElse(through);
    }

    @Override
    public long countUnreadComments(Long agentId, Long postId) {
        Post post = loadPost(postId);
        validateOwned(agentId, post);
        BlockedUserIdsParameter blocked = blocked(post);
        return commentRepository.countUnreadCommentsOnAgentPost(agentId, postId, blocked.empty(), blocked.ids());
    }

    private Post loadPost(Long postId) {
        return postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private BlockedUserIdsParameter blocked(Post post) {
        return BlockedUserIdsParameter.from(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(post.getUserId()));
    }

    private void validateOwned(Long agentId, Post post) {
        if (Boolean.TRUE.equals(post.getIsDeleted()) || Boolean.TRUE.equals(post.getIsBlinded())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        if (!Objects.equals(post.getAgentId(), agentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
