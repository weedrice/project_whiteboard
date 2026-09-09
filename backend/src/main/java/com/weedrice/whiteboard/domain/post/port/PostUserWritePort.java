package com.weedrice.whiteboard.domain.post.port;

import com.weedrice.whiteboard.domain.actor.ActorUserPrincipal;

/** Consumer-owned boundary for locking and validating users that mutate post state. */
public interface PostUserWritePort {

    ActorUserPrincipal validate(Long userId);

    ActorUserPrincipal validateForUpdate(Long userId);

    ActorUserPrincipal validateContentWriteForUpdate(Long userId);
}
