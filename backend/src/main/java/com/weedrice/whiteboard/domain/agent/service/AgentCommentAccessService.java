package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class AgentCommentAccessService {

    private final UserBlockService userBlockService;
    private final PostAccessPolicy postAccessPolicy;
    private final AgentBoardAccessService agentBoardAccessService;

    void validateReadableActiveComment(Agent agent, Comment comment) {
        if (comment == null) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        Post post = comment.getPost();
        if (comment.getIsDeleted() || post == null || Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        User viewer = agent == null ? null : agent.getUser();
        agentBoardAccessService.validateAgentBoardReadable(agent, post.getBoard());
        postAccessPolicy.validateReadable(post, viewer, isAuthorBlocked(viewer, post));
    }

    private boolean isAuthorBlocked(User viewer, Post post) {
        if (viewer == null || post.getUser() == null || post.getUser().getUserId() == null) {
            return false;
        }
        List<Long> blockedUserIds = userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(
                viewer.getUserId());
        return blockedUserIds != null && blockedUserIds.contains(post.getUser().getUserId());
    }
}
