package com.weedrice.whiteboard.domain.post.port;

import com.weedrice.whiteboard.domain.actor.ActorUserPrincipal;

import java.util.List;

/** Consumer-owned boundary for user state required by post reads. */
public interface PostUserReadPort {

    ActorUserPrincipal resolve(Long userId);

    ActorUserPrincipal findOrNull(Long userId);

    Long requireExistingUserId(Long userId);

    Long requireActiveUserId(Long userId);

    boolean isBlockedEitherDirection(Long firstUserId, Long secondUserId);

    List<Long> getBlockedUserIds(Long userId);

    List<Long> getBlockedUserIdsForExistingUser(Long userId);
}
