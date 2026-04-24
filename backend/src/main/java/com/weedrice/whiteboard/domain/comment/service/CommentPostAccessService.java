package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class CommentPostAccessService {

    private final UserBlockService userBlockService;
    private final PostAccessPolicy postAccessPolicy;

    CommentReadContext resolveReadContext(User viewer) {
        if (viewer == null) {
            return new CommentReadContext(null, null);
        }
        return new CommentReadContext(viewer, userBlockService.getBlockedUserIds(viewer.getUserId()));
    }

    void validateReadable(Post post, CommentReadContext context) {
        User viewer = context.viewer();
        List<Long> blockedUserIds = context.blockedUserIds();
        boolean authorBlocked = viewer != null
                && blockedUserIds != null
                && blockedUserIds.contains(post.getUser().getUserId());
        postAccessPolicy.validateReadable(post, viewer, authorBlocked);
    }
}
