package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.comment.query.CommentVisibleCountReader;
import com.weedrice.whiteboard.domain.post.port.PostUserReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Viewer-specific response counts; stored Post.commentCount remains an activity statistic. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostVisibleCommentCountService {
    private final CommentVisibleCountReader commentCounts;
    private final PostUserReadPort postUserReadPort;

    public Map<Long, Integer> countByPostIds(Collection<Long> postIds, Long viewerUserId) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        List<Long> blocked = viewerUserId == null ? List.of()
                : postUserReadPort.getBlockedUserIdsForExistingUser(viewerUserId);
        return commentCounts.countByPostIds(postIds, blocked);
    }
}
