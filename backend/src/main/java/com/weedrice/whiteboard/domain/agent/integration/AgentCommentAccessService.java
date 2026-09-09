package com.weedrice.whiteboard.domain.agent.integration;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentCommentAccessService {

    private final UserBlockService userBlockService;
    private final PostAccessPolicy postAccessPolicy;
    private final AgentBoardAccessService agentBoardAccessService;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public void validateReadableActiveComment(Agent agent, Comment comment) {
        if (comment == null) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        Post post = postRepository.findByIdWithRelations(comment.getPostId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (comment.getIsDeleted() || post == null || Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        User viewer = agent == null ? null : userRepository.findById(agent.getUserId()).orElse(null);
        agentBoardAccessService.validateAgentBoardReadable(agent, post.getBoard());
        postAccessPolicy.validateReadable(post, viewer, isAuthorBlocked(viewer, post));
    }

    private boolean isAuthorBlocked(User viewer, Post post) {
        if (viewer == null || post.getUserId() == null) {
            return false;
        }
        List<Long> blockedUserIds = userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(
                viewer.getUserId());
        return blockedUserIds != null && blockedUserIds.contains(post.getUserId());
    }
}
