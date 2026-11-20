package com.weedrice.whiteboard.domain.post.port;

import java.util.Collection;
import java.util.Set;

/** Consumer-owned boundary for comment state needed by post reads and view history. */
public interface PostCommentStatusPort {
    Set<Long> findPostIdsWithNonAuthorComments(Collection<Long> postIds);
    void requireCommentOnPost(Long postId, Long commentId);
    Long requireActiveCommentId(Long postId, Long commentId);
}
