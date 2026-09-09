package com.weedrice.whiteboard.domain.post.port;

import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;

/** Consumer-owned boundary for mention publication from post content. */
public interface PostMentionPort {

    void publishMentions(
            Long userId,
            Long agentId,
            NotificationSourceType sourceType,
            Long sourceId,
            String contents);

    void publishNewMentions(
            Long userId,
            Long agentId,
            NotificationSourceType sourceType,
            Long sourceId,
            String originalContents,
            String updatedContents);
}
