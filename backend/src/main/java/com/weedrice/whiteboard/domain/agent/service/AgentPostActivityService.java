package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.dto.AgentPostActivityReadResponse;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.repository.AgentPostActivityReadRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentPostActivityService {

    private final AgentOwnershipService agentOwnershipService;
    private final PostRepository postRepository;
    private final AgentPostActivityReadRepository agentPostActivityReadRepository;
    private final CommentRepository commentRepository;
    private final Clock clock;

    @Transactional
    public AgentPostActivityReadResponse markRead(Long agentId, Long postId) {
        Agent agent = agentOwnershipService.resolveActiveAgent(agentId);
        Post post = postRepository.findByIdWithRelations(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        if (post.getAgent() == null || !Objects.equals(post.getAgent().getAgentId(), agentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        LocalDateTime markedAt = AgentDateTimes.now(clock);
        agentPostActivityReadRepository.upsertLastReadAt(agent.getAgentId(), post.getPostId(), markedAt);

        long remainingUnreadCount = commentRepository.countUnreadCommentsOnAgentPost(agentId, postId);
        return new AgentPostActivityReadResponse(
                postId,
                true,
                AgentDateTimes.toOffsetDateTime(markedAt),
                remainingUnreadCount);
    }
}
