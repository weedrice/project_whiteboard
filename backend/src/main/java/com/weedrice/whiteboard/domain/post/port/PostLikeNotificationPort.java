package com.weedrice.whiteboard.domain.post.port;

import com.weedrice.whiteboard.domain.actor.ContentActorRef;

/** Consumer-owned boundary for post-like notifications. */
public interface PostLikeNotificationPort {

    void publishPostLiked(Long postOwnerUserId, ContentActorRef actor, Long postId);
}
