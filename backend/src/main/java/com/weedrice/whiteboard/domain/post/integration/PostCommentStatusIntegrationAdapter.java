package com.weedrice.whiteboard.domain.post.integration;

import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.port.PostCommentStatusPort;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostCommentStatusIntegrationAdapter implements PostCommentStatusPort {
    private final CommentRepository commentRepository;

    @Override
    public Set<Long> findPostIdsWithNonAuthorComments(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(commentRepository.findPostIdsWithNonAuthorCommentsByPostIds(List.copyOf(postIds)));
    }

    @Override
    public Long requireActiveCommentId(Long postId, Long commentId) {
        return commentRepository.findByCommentIdAndPostIdAndIsDeletedFalse(commentId, postId)
                .map(comment -> comment.getCommentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE));
    }
}
