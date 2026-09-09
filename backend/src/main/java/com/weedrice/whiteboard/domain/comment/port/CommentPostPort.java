package com.weedrice.whiteboard.domain.comment.port;

import java.util.Collection;
import java.util.Map;

public interface CommentPostPort {
    CommentPostSnapshot getRequired(Long postId);

    Map<Long, CommentPostSnapshot> getAll(Collection<Long> postIds);

    CommentPostSnapshot lockForWrite(Long postId);

    default void validateReadable(Long postId, Long viewerUserId) {
        validateReadable(postId, viewerUserId, null);
    }

    void validateReadable(Long postId, Long viewerUserId, Collection<Long> blockedUserIds);

    void validateWritable(Long postId, Long writerUserId);

    void incrementCommentCount(Long postId);

    void decrementCommentCount(Long postId);
}
