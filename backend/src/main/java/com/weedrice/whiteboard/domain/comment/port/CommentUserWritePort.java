package com.weedrice.whiteboard.domain.comment.port;

import java.util.Collection;
import java.util.List;

/** Consumer-owned boundary for user validation and mention delivery during comment writes. */
public interface CommentUserWritePort {

    void validateWritable(Long userId);

    List<Long> resolveMentionedUserIds(Long authorUserId, Collection<Long> mentionedUserIds);

    void publishMentions(
            Long actorUserId,
            Long actorAgentId,
            Long commentId,
            String content,
            Collection<Long> mentionedUserIds);

    void publishNewMentions(
            Long actorUserId,
            Long actorAgentId,
            Long commentId,
            Collection<Long> previousMentionedUserIds,
            Collection<Long> currentMentionedUserIds);
}
