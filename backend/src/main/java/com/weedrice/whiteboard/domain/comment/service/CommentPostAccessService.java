package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
class CommentPostAccessService {

    private final UserBlockService userBlockService;
    private final PostAccessPolicy postAccessPolicy;

    CommentReadContext resolveReadContext(User viewer) {
        if (viewer == null) {
            return new CommentReadContext(null, Set.of());
        }
        List<Long> blockedUserIds = userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(
                viewer.getUserId());
        Set<Long> blockedUserIdSet = blockedUserIds == null || blockedUserIds.isEmpty()
                ? Set.of()
                : Set.copyOf(blockedUserIds);
        return new CommentReadContext(viewer, blockedUserIdSet);
    }

    void validateReadable(Post post, CommentReadContext context) {
        User viewer = context.viewer();
        Set<Long> blockedUserIds = context.blockedUserIds();
        boolean authorBlocked = viewer != null
                && blockedUserIds.contains(post.getUser().getUserId());
        postAccessPolicy.validateReadable(post, viewer, authorBlocked);
    }
}
