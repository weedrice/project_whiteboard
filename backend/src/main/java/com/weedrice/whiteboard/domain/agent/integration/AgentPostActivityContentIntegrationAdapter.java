package com.weedrice.whiteboard.domain.agent.integration;

import com.weedrice.whiteboard.domain.agent.port.AgentPostActivityContentPort;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class AgentPostActivityContentIntegrationAdapter implements AgentPostActivityContentPort {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Override
    public void validateOwnedActivePost(Long agentId, Long postId) {
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        if (post.getAgentId() == null || !Objects.equals(post.getAgentId(), agentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    @Override
    public long countUnreadComments(Long agentId, Long postId) {
        return commentRepository.countUnreadCommentsOnAgentPost(agentId, postId);
    }
}
